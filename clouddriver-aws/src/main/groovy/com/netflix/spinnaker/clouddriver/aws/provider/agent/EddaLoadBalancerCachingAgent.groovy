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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApi
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.clouddriver.aws.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider

import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.INSTANCES

import groovy.util.logging.Slf4j

@Slf4j
class EddaLoadBalancerCachingAgent implements CachingAgent, HealthProvidingCachingAgent, AccountAware {
  private final EddaApi eddaApi
  private final NetflixAmazonCredentials account
  private final String region
  private final ObjectMapper objectMapper
  final String healthId = "edda-load-balancers"

  EddaLoadBalancerCachingAgent(EddaApi eddaApi, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.eddaApi = eddaApi
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${EddaLoadBalancerCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    List<LoadBalancerInstanceState> balancerInstances = eddaApi.loadBalancerInstances()

    List<InstanceLoadBalancers> ilbs = InstanceLoadBalancers.fromLoadBalancerInstanceState(balancerInstances)
    Collection<CacheData> lbHealths = new ArrayList<CacheData>(ilbs.size())
    Collection<CacheData> instances = new ArrayList<CacheData>(ilbs.size())

    for (InstanceLoadBalancers ilb : ilbs) {
      String instanceId = Keys.getInstanceKey(ilb.instanceId, account.name, region)
      String healthId = Keys.getInstanceHealthKey(ilb.instanceId, account.name, region, healthId)
      Map<String, Object> attributes = objectMapper.convertValue(ilb, ATTRIBUTES)
      Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
      lbHealths.add(new DefaultCacheData(healthId, attributes, relationships))
      instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
    }
    log.info("Caching ${instances.size()} items in ${agentType}")

    new DefaultCacheResult(
      (HEALTH.ns): lbHealths,
      (INSTANCES.ns): instances)
  }
}
