/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.subnet.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import groovy.util.logging.Slf4j

@Slf4j
class AzureSubnetCachingAgent implements CachingAgent, AccountAware {

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials account
  final String region
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.SUBNETS.ns)
  ] as Set)

  AzureSubnetCachingAgent(AzureCloudProvider azureCloudProvider,
                           String accountName,
                           AzureCredentials account,
                           String region,
                           ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.accountName = accountName
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${AzureSubnetCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    /*
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    def subnets = ec2.describeSubnets().subnets

    List<CacheData> data = subnets.collect { Subnet subnet ->
      Map<String, Object> attributes = objectMapper.convertValue(subnet, AwsInfrastructureProvider.ATTRIBUTES)
      new DefaultCacheData(Keys.getSubnetKey(amazonCloudProvider, subnet.subnetId, region, account.name),
        attributes,
        [:])
    }
    */
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(Keys.Namespace.SUBNETS.ns): data])
  }
}
