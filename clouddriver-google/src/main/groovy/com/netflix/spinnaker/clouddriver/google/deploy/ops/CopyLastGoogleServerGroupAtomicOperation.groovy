/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AutoscalingPolicy
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy
import com.google.api.services.compute.model.InstanceProperties
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.deploy.GCEServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.handlers.BasicGoogleDeployHandler
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastGoogleServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final BasicGoogleDeployDescription description

  @Autowired
  BasicGoogleDeployHandler basicGoogleDeployHandler

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  CopyLastGoogleServerGroupAtomicOperation(BasicGoogleDeployDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "cloneServerGroup": { "source": { "region": "us-central1", "serverGroupName": "myapp-dev-v000" }, "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "cloneServerGroup": { "source": { "region": "us-central1", "serverGroupName": "myapp-dev-v000" }, "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 4, "instanceType": "g1-small", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    BasicGoogleDeployDescription newDescription = cloneAndOverrideDescription()


    def credentials = newDescription.credentials
    def project = credentials.project
    def isRegional = newDescription.regional
    def zone = newDescription.zone
    def region = newDescription.region ?: credentials.regionFromZone(zone)
    def serverGroupNameResolver = new GCEServerGroupNameResolver(project, region, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(newDescription.application, newDescription.stack, newDescription.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing copy of server group for cluster $clusterName in ${isRegional ? region : zone}..."

    def result = basicGoogleDeployHandler.handle(newDescription, priorOutputs)
    def newServerGroupName = getServerGroupName(result?.serverGroupNames?.getAt(0))

    task.updateStatus BASE_PHASE, "Finished copying server group for cluster $clusterName. " +
                                  "New server group = $newServerGroupName in ${isRegional ? region : zone}."

    result
  }

  private BasicGoogleDeployDescription cloneAndOverrideDescription() {
    BasicGoogleDeployDescription newDescription = description.clone()

    if (!description?.source?.region || !description?.source?.serverGroupName) {
      return newDescription
    }

    task.updateStatus BASE_PHASE, "Initializing copy of server group $description.source.serverGroupName..."

    // Locate the ancestor server group.
    def ancestorServerGroup = GCEUtil.queryServerGroup(googleClusterProvider,
                                                       description.accountName,
                                                       description.source.region,
                                                       description.source.serverGroupName)

    if (!ancestorServerGroup) {
      return newDescription
    }

    def ancestorNames = Names.parseName(ancestorServerGroup.name)

    // Override any ancestor values that were specified directly on the cloneServerGroup call.
    newDescription.region = description.region ?: Utils.getLocalName(ancestorServerGroup.region)
    newDescription.regional =
        description.regional != null
        ? description.regional
        : ancestorServerGroup.regional
    newDescription.zone = description.zone ?: Utils.getLocalName(ancestorServerGroup.zone)
    newDescription.loadBalancers =
        description.loadBalancers != null
        ? description.loadBalancers
        : (ancestorServerGroup.loadBalancers as List)
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.targetSize =
        description.targetSize != null
        ? description.targetSize
        : ancestorServerGroup.capacity.desired

    def accountName = description.accountName
    def ancestorInstanceTemplate = ancestorServerGroup.launchConfig.instanceTemplate

    if (ancestorInstanceTemplate) {
      // Override any ancestor values that were specified directly on the call.
      InstanceProperties ancestorInstanceProperties = ancestorInstanceTemplate.properties

      newDescription.instanceType = description.instanceType ?: ancestorInstanceProperties.machineType

      List<AttachedDisk> attachedDisks = ancestorInstanceProperties?.disks

      if (attachedDisks) {
        def bootDisk = attachedDisks.find { it.boot }

        newDescription.image = description.image ?: GCEUtil.getLocalName(bootDisk.initializeParams.sourceImage)
        newDescription.disks = description.disks ?: attachedDisks.collect { attachedDisk ->
          def initializeParams = attachedDisk.initializeParams

          new GoogleDisk(type: initializeParams.diskType,
                         sizeGb: initializeParams.diskSizeGb,
                         autoDelete: attachedDisk.autoDelete)
        }
      }

      def instanceMetadata = ancestorInstanceProperties.metadata

      if (instanceMetadata) {
        newDescription.instanceMetadata =
            description.instanceMetadata != null
            ? description.instanceMetadata
            : GCEUtil.buildMapFromMetadata(instanceMetadata)
      }

      def tags = ancestorInstanceProperties.tags

      if (tags != null) {
        newDescription.tags = description.tags != null ? description.tags : tags.items
      }

      def scheduling = ancestorInstanceProperties.scheduling

      if (scheduling) {
        newDescription.preemptible =
            description.preemptible != null
            ? description.preemptible
            : scheduling.preemptible
        newDescription.automaticRestart =
            description.automaticRestart != null
            ? description.automaticRestart
            : scheduling.automaticRestart
        newDescription.onHostMaintenance =
            description.onHostMaintenance != null
            ? description.onHostMaintenance
            : scheduling.onHostMaintenance
      }

      newDescription.serviceAccountEmail =
          description.serviceAccountEmail != null
          ? description.serviceAccountEmail
          : ancestorInstanceProperties.serviceAccounts?.getAt(0)?.email

      newDescription.authScopes =
          description.authScopes != null
          ? description.authScopes
          : GCEUtil.retrieveScopesFromServiceAccount(newDescription.serviceAccountEmail,
                                                     ancestorInstanceProperties.serviceAccounts)

      newDescription.network =
        GCEUtil.getLocalName(description.network ?: ancestorInstanceProperties.networkInterfaces?.getAt(0)?.network)

      newDescription.subnet =
          description.subnet != null
          ? description.subnet
          : GCEUtil.getLocalName(ancestorInstanceProperties.networkInterfaces?.getAt(0)?.subnetwork)

      Set<GoogleSecurityGroup> googleSecurityGroups = googleSecurityGroupProvider.getAllByAccount(false, accountName)

      // Find all firewall rules with target tags matching the tags of the ancestor instance template.
      def googleSecurityGroupMatches = [] as Set

      ancestorInstanceTemplate?.properties?.tags?.items.each { instanceTemplateTag ->
        googleSecurityGroupMatches << googleSecurityGroups.findAll { googleSecurityGroup ->
          googleSecurityGroup.targetTags?.contains(instanceTemplateTag)
        }
      }

      Set<GoogleSecurityGroup> ancestorSecurityGroups = googleSecurityGroupMatches.flatten().collect { it.name }

      if (newDescription.securityGroups == null) {
        // Since no security groups were specified, use the security groups of the ancestor server group.
        newDescription.securityGroups = ancestorSecurityGroups
      } else {
        // Since security groups were specified, we must back out the tags of the security groups that are associated
        // with the ancestor server group but not with the cloned server group.
        if (newDescription.tags) {
          Set<String> elidedSecurityGroupNames = ancestorSecurityGroups - newDescription.securityGroups

          if (elidedSecurityGroupNames) {
            Set<String> elidedSecurityGroupsTargetTags = GCEUtil.querySecurityGroupTags(elidedSecurityGroupNames,
                                                                                        newDescription.accountName,
                                                                                        googleSecurityGroupProvider,
                                                                                        task,
                                                                                        BASE_PHASE)

            newDescription.tags -= elidedSecurityGroupsTargetTags
          }
        }
      }
    }

    AutoscalingPolicy ancestorAutoscalingPolicy = ancestorServerGroup.autoscalingPolicy
    GoogleAutoscalingPolicy ancestorAutoscalingPolicyDescription =
      GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(ancestorAutoscalingPolicy)

    newDescription.autoscalingPolicy = description.autoscalingPolicy ?: ancestorAutoscalingPolicyDescription

    InstanceGroupManagerAutoHealingPolicy ancestorAutoHealingPolicy = ancestorServerGroup.autoHealingPolicy
    BasicGoogleDeployDescription.AutoHealingPolicy ancestorAutoHealingPolicyDescription =
      GCEUtil.buildAutoHealingPolicyDescriptionFromAutoHealingPolicy(ancestorAutoHealingPolicy)

    newDescription.autoHealingPolicy = description.autoHealingPolicy ?: ancestorAutoHealingPolicyDescription

    return newDescription
  }

  private static String getServerGroupName(String regionPlusServerGroupName) {
    if (!regionPlusServerGroupName) {
      return 'Unknown'
    }

    def nameParts = regionPlusServerGroupName.split(":")

    return nameParts[nameParts.length - 1]
  }
}
