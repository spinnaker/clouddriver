package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.handlers.TencentDeployHandler
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient

class CloneTencentServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "CLONE_SERVER_GROUP"

  TencentDeployDescription description

  @Autowired
  TencentClusterProvider tencentClusterProvider

  @Autowired
  TencentDeployHandler tencentDeployHandler

  CloneTencentServerGroupAtomicOperation(TencentDeployDescription description) {
    this.description = description
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    def newDescription = cloneAndOverrideDescription()
    def result = tencentDeployHandler.handle(newDescription, priorOutputs)
    result
  }

  private TencentDeployDescription cloneAndOverrideDescription() {
    def newDescription = description.clone()

    if (!description?.source?.region || !description?.source?.serverGroupName) {
      return newDescription
    }

    String sourceServerGroupName = description.source.serverGroupName
    String sourceRegion = description.source.region
    String accountName = description.accountName
    task.updateStatus BASE_PHASE, "Initializing copy of server group $sourceServerGroupName..."

    // look up source server group
    def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)

    if (!sourceServerGroup) {
      return newDescription
    }

    // start override source description
    newDescription.region = description.region ?: sourceRegion
    newDescription.application = description.application ?: sourceServerGroup.moniker.app
    newDescription.stack = description.stack ?: sourceServerGroup.moniker.stack
    newDescription.detail = description.detail ?: sourceServerGroup.moniker.detail

    def sourceLaunchConfig = sourceServerGroup.launchConfig
    if (sourceLaunchConfig) {
      newDescription.instanceType = description.instanceType ?: sourceLaunchConfig.instanceType
      newDescription.imageId = description.imageId ?: sourceLaunchConfig.imageId
      newDescription.projectId = description.projectId ?: sourceLaunchConfig.projectId as Integer
      newDescription.systemDisk = description.systemDisk ?: sourceLaunchConfig.systemDisk as Map
      newDescription.dataDisks = description.dataDisks ?: sourceLaunchConfig.dataDisks as List
      newDescription.internetAccessible = description.internetAccessible ?: sourceLaunchConfig.internetAccessible as Map
      newDescription.loginSettings = description.loginSettings ?: sourceLaunchConfig.loginSettings as Map
      newDescription.securityGroupIds = description.securityGroupIds ?: sourceLaunchConfig.securityGroupIds as List
      newDescription.enhancedService = description.enhancedService ?: sourceLaunchConfig.enhancedService as Map
      newDescription.userData = description.userData ?: sourceLaunchConfig.userData
      newDescription.instanceChargeType = description.instanceChargeType ?: sourceLaunchConfig.instanceChargeType
      // newDescription.instanceTypes = description.instanceTypes ?: sourceLaunchConfig.instanceTypes as List
      newDescription.instanceMarketOptionsRequest = description.instanceMarketOptionsRequest ?: sourceLaunchConfig.instanceMarketOptionsRequest as Map
      newDescription.instanceTypesCheckPolicy = description.instanceTypesCheckPolicy ?: sourceLaunchConfig.instanceTypesCheckPolicy



      if (description.instanceTags) {
        newDescription.instanceTags = description.instanceTags
      } else if (newDescription.instanceTags) {
        def cloneInstanceTags = []
        for (tag in newDescription.instanceTags) {
          if (tag.key != AutoScalingClient.defaultServerGroupTagKey) {
            cloneInstanceTags.add(tag)
          }
        }
        newDescription.instanceTags = cloneInstanceTags
      }
    }

    def sourceAutoScalingGroup = sourceServerGroup.asg
    if (sourceAutoScalingGroup) {
      newDescription.maxSize = description.maxSize ?: sourceAutoScalingGroup.maxSize as Integer
      newDescription.minSize = description.minSize ?: sourceAutoScalingGroup.minSize as Integer
      newDescription.desiredCapacity = description.desiredCapacity ?: sourceAutoScalingGroup.desiredCapacity as Integer
      newDescription.vpcId = description.vpcId ?: sourceAutoScalingGroup.vpcId

      if (newDescription.vpcId) {
        newDescription.subnetIds = description.subnetIds ?: sourceAutoScalingGroup.subnetIdSet as List
      } else {
        newDescription.zones = description.zones ?: sourceAutoScalingGroup.zoneSet as List
      }

      newDescription.defaultCooldown = description.defaultCooldown ?: sourceAutoScalingGroup.defaultCooldown as Integer
      newDescription.terminationPolicies = description.terminationPolicies ?: sourceAutoScalingGroup.terminationPolicies as List
      newDescription.loadBalancerIds = description.loadBalancerIds ?: sourceAutoScalingGroup.loadBalancerIds as List
      newDescription.forwardLoadBalancers = description.forwardLoadBalancers ?: sourceAutoScalingGroup.forwardLoadBalancerSet as List
      newDescription.retryPolicy = description.retryPolicy ?: sourceAutoScalingGroup.retryPolicy
      newDescription.zonesCheckPolicy = description.zonesCheckPolicy ?: sourceAutoScalingGroup.zoneCheckPolicy
    }
    newDescription
  }

    private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
