/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerUpsertHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult.LoadBalancer
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nonnull
import java.util.regex.Matcher
import java.util.regex.Pattern
import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.*

/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerClassicDescription}.
 *
 *
 */
@Slf4j
class UpsertAmazonLoadBalancerAtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerResult> {
  private static final String BASE_PHASE = "CREATE_ELB"
  private static final Pattern HEALTHCHECK_PORT_PATTERN = Pattern.compile("\\d+")

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  private final UpsertAmazonLoadBalancerClassicDescription description
  ObjectMapper objectMapper = new ObjectMapper()

  UpsertAmazonLoadBalancerAtomicOperation(UpsertAmazonLoadBalancerDescription description) {
    this.description = (UpsertAmazonLoadBalancerClassicDescription) description
  }

  @Override
  UpsertAmazonLoadBalancerResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new UpsertAmazonLoadBalancerResult(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def loadBalancerName = description.name ?: "${description.clusterName}-frontend".toString()

      //maintains bwc with the contains internal check.
      boolean isInternal = description.getIsInternal() != null ? description.getIsInternal() : description.subnetType?.contains('internal')

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region, true)

      LoadBalancerDescription loadBalancer = null

      task.updateStatus BASE_PHASE, "Setting up listeners for ${loadBalancerName} in ${region}..."
      def listeners = []
      description.listeners
        .each { UpsertAmazonLoadBalancerClassicDescription.Listener listener ->
          def awsListener = new Listener()
          awsListener.withLoadBalancerPort(listener.externalPort).withInstancePort(listener.internalPort)

          awsListener.withProtocol(listener.externalProtocol.name())
          if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
            awsListener.withInstanceProtocol(listener.internalProtocol.name())
          } else {
            awsListener.withInstanceProtocol(listener.externalProtocol.name())
          }
          if (listener.sslCertificateId) {
            task.updateStatus BASE_PHASE, "Attaching listener with SSL ServerCertificate: ${listener.sslCertificateId}"
            awsListener.withSSLCertificateId(listener.sslCertificateId)
          }
          listeners << awsListener
          task.updateStatus BASE_PHASE, "Appending listener ${awsListener.protocol}:${awsListener.loadBalancerPort} -> ${awsListener.instanceProtocol}:${awsListener.instancePort}"
        }

      try {
        loadBalancer = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest([loadBalancerName]))?.
                loadBalancerDescriptions?.getAt(0)
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
      } catch (AmazonServiceException ignore) {
      }

      def securityGroupNamesToIds = regionScopedProvider.securityGroupService.getSecurityGroupIds(description.securityGroups, description.vpcId)
      if (description.name == description.application) {
        try {
          String applicationLoadBalancerSecurityGroupId = ingressApplicationLoadBalancerGroup(
            description,
            region,
            listeners,
            securityGroupLookupFactory
          )

          securityGroupNamesToIds.put(description.application + "-elb", applicationLoadBalancerSecurityGroupId)
          task.updateStatus BASE_PHASE, "Authorized application Load Balancer Security Group $applicationLoadBalancerSecurityGroupId"
        } catch (Exception e) {
          log.error("Failed to authorize app ELB security group {}-elb on application security group", description.name,  e)
          task.updateStatus BASE_PHASE, "Failed to authorize app ELB security group ${description.name}-elb on application security group"
        }
      }

      def securityGroups = securityGroupNamesToIds.values()
      String dnsName
      if (!loadBalancer) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB, 1)
        }
        dnsName = LoadBalancerUpsertHandler.createLoadBalancer(loadBalancing, loadBalancerName, isInternal, availabilityZones, subnetIds, listeners, securityGroups)
      } else {
        dnsName = loadBalancer.DNSName
        LoadBalancerUpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, listeners, securityGroups)
      }

      // Configure health checks
      if (description.healthCheck) {
        task.updateStatus BASE_PHASE, "Configuring healthcheck for ${loadBalancerName} in ${region}..."
        def healthCheck = new ConfigureHealthCheckRequest(loadBalancerName, new HealthCheck()
                .withTarget(description.healthCheck).withInterval(description.healthInterval)
                .withTimeout(description.healthTimeout).withUnhealthyThreshold(description.unhealthyThreshold)
                .withHealthyThreshold(description.healthyThreshold))
        loadBalancing.configureHealthCheck(healthCheck)
        task.updateStatus BASE_PHASE, "Healthcheck configured."
      }

      // Apply balancing opinions...
      loadBalancing.modifyLoadBalancerAttributes(
        new ModifyLoadBalancerAttributesRequest(loadBalancerName: loadBalancerName)
          .withLoadBalancerAttributes(
          new LoadBalancerAttributes(
            crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: description.crossZoneBalancing)
          )
        )
      )

      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  private static String ingressApplicationLoadBalancerGroup(UpsertAmazonLoadBalancerClassicDescription description,
                                                            String region,
                                                            List<Listener> loadBalancerListeners,
                                                            SecurityGroupLookupFactory securityGroupLookupFactory) {
    SecurityGroupLookup securityGroupLookup = securityGroupLookupFactory.getInstance(region)
    String applicationSecurityGroupName = description.application
    String applicationLoadBalancerSecurityGroupName = description.application + "-elb"

    // 1. get app load balancer security group & app security group. create if doesn't exist
    String applicationLoadBalancerSecurityGroupId = getOrCreateSecurityGroup(
      applicationLoadBalancerSecurityGroupName,
      description.vpcId,
      description.credentialAccount,
      region,
      "Application ELB Security Group for $description.application",
      securityGroupLookup
    )

    String applicationSecurityGroupId = getOrCreateSecurityGroup(
      applicationSecurityGroupName,
      description.vpcId,
      description.credentialAccount,
      region,
      "Application Security Group for $description.application",
      securityGroupLookup
    )

    IngressSecurityGroupDescription applicationSecurityGroupDescription = new IngressSecurityGroupDescription(
      applicationSecurityGroupId,
      description.name,
      description.credentialAccount,
      description.vpcId
    ).withHealthCheck(description.healthCheck).withListeners(loadBalancerListeners)

    IngressSecurityGroupDescription applicationLoadBalancerSecurityGroupDescription = new IngressSecurityGroupDescription(
      applicationLoadBalancerSecurityGroupId,
      applicationLoadBalancerSecurityGroupName,
      description.credentialAccount,
      description.vpcId
    )

    ingressSourceSecurityGroup(
      applicationLoadBalancerSecurityGroupDescription,
      applicationSecurityGroupDescription,
      securityGroupLookup
    )

    return applicationLoadBalancerSecurityGroupId
  }

  private static String getOrCreateSecurityGroup(String groupName,
                                                 String vpcId,
                                                 String account,
                                                 String region,
                                                 String description,
                                                 SecurityGroupLookup securityGroupLookup) {
    String groupId = null
    OperationPoller.retryWithBackoff({
      def securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
        account,
        groupName,
        vpcId
      ).orElse(null)

      if (!securityGroupUpdater) {
        securityGroupUpdater = securityGroupLookup.createSecurityGroup(
          new UpsertSecurityGroupDescription(
            name: groupName,
            description: description,
            vpcId: vpcId,
            region: region
          )
        )
      }

      groupId =  securityGroupUpdater?.getSecurityGroup()?.groupId
    }, 500, 3)

    return groupId
  }

  private static void ingressSourceSecurityGroup(IngressSecurityGroupDescription source,
                                                 IngressSecurityGroupDescription target,
                                                 SecurityGroupLookup securityGroupLookup) throws FailedSecurityGroupIngressException {
    def targetSecurityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
      target.account,
      target.name,
      target.vpcId
    ).orElse(null)

    if (targetSecurityGroupUpdater) {
      List<IpPermission> currentPermissions = SecurityGroupIngressConverter.flattenPermissions(targetSecurityGroupUpdater.securityGroup)
      List<IpPermission> targetPermissions = target.listeners.collect {
        new IpPermission(
          ipProtocol: "tcp",
          fromPort: it.getInstancePort(),
          toPort: it.getInstancePort(),
          userIdGroupPairs: [
            new UserIdGroupPair().withGroupId(source.id)
          ]
        )
      }

      // include health check endpoint
      Matcher portMatcher = HEALTHCHECK_PORT_PATTERN.matcher(target.healthCheck)
      int healthCheckPort
      if (target.healthCheck && portMatcher.find()) {
        healthCheckPort = portMatcher.group() as int
        def rules = targetPermissions.find { it.fromPort == healthCheckPort && it.toPort == healthCheckPort }
        if (!rules) {
          targetPermissions.add(
            new IpPermission(
              ipProtocol: "tcp",
              fromPort: healthCheckPort,
              toPort: healthCheckPort,
              userIdGroupPairs: [
                new UserIdGroupPair().withGroupId(source.id)
              ]
            )
          )
        }
      }

      filterOutExistingPermissions(targetPermissions, currentPermissions)
      if (targetPermissions) {
        try {
          targetSecurityGroupUpdater.addIngress(targetPermissions)
        } catch (Exception e) {
          throw new FailedSecurityGroupIngressException(e)
        }
      }
    }
  }

  private static void filterOutExistingPermissions(List<IpPermission> permissionsToAdd,
                                                   List<IpPermission> existingPermissions) {
    permissionsToAdd.each { permission ->
      permission.getUserIdGroupPairs().removeIf { pair ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            pair.groupId && pair.groupId in p.userIdGroupPairs*.groupId
        } != null
      }

      permission.getIpv4Ranges().removeIf { range ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            range in p.ipv4Ranges
        } != null
      }

      permission.getIpv6Ranges().removeIf { range ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            range in p.ipv6Ranges
        } != null
      }
    }

    permissionsToAdd.removeIf { permission -> !permission.userIdGroupPairs }
  }

  static class IngressSecurityGroupDescription {
    @Nonnull String id
    @Nonnull String name
    @Nonnull String account
    @Nonnull String vpcId
    String healthCheck
    List<Listener> listeners

    IngressSecurityGroupDescription(String id, String name, String account, String vpcId) {
      this.id = id
      this.name = name
      this.account = account
      this.vpcId = vpcId
    }

    IngressSecurityGroupDescription withListeners(List<Listener> listeners) {
      this.listeners = listeners
      return this
    }

    IngressSecurityGroupDescription withHealthCheck(String healthCheck) {
      this.healthCheck = healthCheck
      return this
    }
  }

  static class FailedSecurityGroupIngressException extends Exception {
    FailedSecurityGroupIngressException(Throwable throwable) {
      super(throwable)
    }
  }
}
