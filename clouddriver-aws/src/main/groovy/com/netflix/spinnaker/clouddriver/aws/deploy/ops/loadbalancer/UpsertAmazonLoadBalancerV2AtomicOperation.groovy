/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancingv2.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerV2UpsertHandler
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerV2Description}.
 *
 *
 */
class UpsertAmazonLoadBalancerV2AtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerV2Result> {
  private static final String BASE_PHASE = "CREATE_ELB_V2"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  private final UpsertAmazonLoadBalancerV2Description description
  ObjectMapper objectMapper = new ObjectMapper()

  UpsertAmazonLoadBalancerV2AtomicOperation(UpsertAmazonLoadBalancerDescription description) {
    this.description = (UpsertAmazonLoadBalancerV2Description) description
  }

  @Override
  UpsertAmazonLoadBalancerV2Result operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new UpsertAmazonLoadBalancerV2Result(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def loadBalancerName = description.name ?: "${description.clusterName}-frontend".toString()

      //maintains bwc with the contains internal check.
      boolean isInternal = description.getInternal() != null ? description.getInternal() : description.subnetType?.contains('internal')

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.credentials, region, true)

      // Set up security groups
      def securityGroups = regionScopedProvider.securityGroupService.
              getSecurityGroupIds(description.securityGroups, description.vpcId).values()

      // Check if load balancer already exists
      LoadBalancer loadBalancer
      try {
        DescribeLoadBalancersResult result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: [loadBalancerName] ))
        loadBalancer = result.loadBalancers.size() > 0 ? result.loadBalancers.get(0) : null
      } catch (AmazonServiceException ignore) {
      }

      // Create/Update load balancer
      String dnsName
      if (loadBalancer == null) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB, 1)
        }
        dnsName = LoadBalancerV2UpsertHandler.createLoadBalancer(loadBalancing, loadBalancerName, isInternal, subnetIds, securityGroups, description.targetGroups, description.listeners)
      } else {
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
        dnsName = loadBalancer.DNSName
        LoadBalancerV2UpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, securityGroups, description.targetGroups, description.listeners)
      }

      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new UpsertAmazonLoadBalancerV2Result.LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }
}
