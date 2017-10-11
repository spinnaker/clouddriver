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
import com.amazonaws.services.ec2.model.IpRange
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerUpsertHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult.LoadBalancer
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import java.util.regex.Matcher
import java.util.regex.Pattern

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

      LoadBalancerDescription loadBalancer

      task.updateStatus BASE_PHASE, "Setting up listeners for ${loadBalancerName} in ${region}..."
      List<Listener> listeners = []
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
      def securityGroups = securityGroupNamesToIds.values()
      String applicationSecurityGroup = updateApplicationSecurityGroupIngressRules(listeners)
      if (applicationSecurityGroup != null && !securityGroupNamesToIds.containsKey(applicationSecurityGroup)) {
        securityGroups.add(applicationSecurityGroup)
      }

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

  private String updateApplicationSecurityGroupIngressRules(List<Listener> listeners) {
    Names names = Names.parseName(description.name)
    String application = names.getApp()
    final securityGroupLookup = securityGroupLookupFactory.getInstance(description.region)
    def securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
      description.credentialAccount,
      application,
      description.vpcId
    ).orElse(null)

    if (!securityGroupUpdater) {
      log.info("Application security group not found for $application")
      return null
    }

    try {
      List<IpPermission> currentPermissions = SecurityGroupIngressConverter.flattenPermissions(securityGroupUpdater.securityGroup)
      List<IpPermission> permissionsFromListeners = listeners.collect {
        new IpPermission(
          ipProtocol: "tcp",
          fromPort: it.getInstancePort(),
          toPort: it.getInstancePort(),
          ipv4Ranges: Collections.singletonList(new IpRange(cidrIp: "0.0.0.0/0"))
        )
      }

      // ensure to account for port in health check endpoint
      Matcher portMatcher = HEALTHCHECK_PORT_PATTERN.matcher(description.healthCheck)
      int healthCheckPort
      if (description.healthCheck && portMatcher.find()) {
        healthCheckPort = portMatcher.group() as int
        def rules = permissionsFromListeners.find { it.fromPort == healthCheckPort && it.toPort == healthCheckPort }
        if (!rules) {
          permissionsFromListeners.add(
            new IpPermission(
              ipProtocol: "tcp",
              fromPort: healthCheckPort,
              toPort: healthCheckPort,
              ipv4Ranges: Collections.singletonList(new IpRange(cidrIp: "0.0.0.0/0")))
          )
        }
      }

      def newIngress = permissionsFromListeners - currentPermissions
      if (newIngress) {
        task.updateStatus BASE_PHASE, "Configuring ingress rules for application security group $application ..."
        securityGroupUpdater.addIngress(newIngress)
      }

      return securityGroupUpdater.securityGroup.groupId
    } catch (Exception e) {
      log.error("Failed to configure ingress rules from $description.name on application security group $application", e)
      task.updateStatus BASE_PHASE, "Failed to configuring ingress rules for application security group $application"
    }

    return null
  }
}
