/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.eureka.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import com.netflix.spinnaker.clouddriver.eureka.api.EurekaApi
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplication
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplications
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaInstance
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES

@Slf4j
class EurekaCachingAgent implements CachingAgent, HealthProvidingCachingAgent, CustomScheduledAgent {

  private final String region
  private final EurekaApi eurekaApi
  private final ObjectMapper objectMapper
  private final String eurekaHost
  private final String eurekaAccountName
  private final boolean allowMultipleEurekaPerAccount
  final String healthId = "Discovery"
  private final long pollIntervalMillis
  private final long timeoutMillis

  private List<EurekaAwareProvider> eurekaAwareProviderList

  EurekaCachingAgent(EurekaApi eurekaApi,
                     String region,
                     ObjectMapper objectMapper,
                     eurekaHost,
                     allowMultipleEurekaPerAccount,
                     eurekaAccountName,
                     eurekaAwareProviderList,
                     long pollIntervalMillis,
                     long timeoutMillis
  ) {
    this.region = region
    this.eurekaApi = eurekaApi
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.eurekaHost = eurekaHost
    this.allowMultipleEurekaPerAccount = allowMultipleEurekaPerAccount
    this.eurekaAccountName = eurekaAccountName
    this.eurekaAwareProviderList = eurekaAwareProviderList
    this.timeoutMillis = timeoutMillis
    this.pollIntervalMillis = pollIntervalMillis
  }

  @Override
  String getAgentType() {
    "${eurekaHost}/${EurekaCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    'eureka'
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    EurekaApplications disco = eurekaApi.loadEurekaApplications()

    Map<String, Set<String>> instanceHealthRelationships = [:].withDefault { new HashSet<String>() }
    Map<String, List<CacheData>> eurekaInstances = [:].withDefault { [] }

    for (EurekaApplication application : disco.applications) {
      List<Map<String, Object>> instanceAttributes = objectMapper.convertValue(application.instances,
        new TypeReference<List<Map<String, Object>>>() {})

      for (Map<String, Object> attributes : instanceAttributes) {
        if (attributes.instanceId) {
          attributes.eurekaAccountName = eurekaAccountName
          attributes.allowMultipleEurekaPerAccount = allowMultipleEurekaPerAccount
          attributes.application = application.name.toLowerCase()

          eurekaAwareProviderList.each { provider ->
            if (provider.isProviderForEurekaRecord(attributes)) {
              String instanceKey = provider.getInstanceKey(attributes, region)
              if (instanceKey) {
                String instanceHealthKey = provider.getInstanceHealthKey(attributes, region, healthId)
                instanceHealthRelationships[instanceKey].add(instanceHealthKey)
                Map<String, Collection<String>> healthRelationship = [(INSTANCES.ns): [instanceKey]]
                eurekaInstances[instanceHealthKey].add(new DefaultCacheData(instanceHealthKey, attributes, healthRelationship))
              }
            }
          }
        }
      }
    }
    Collection<CacheData> instanceCacheData = instanceHealthRelationships.collect { instanceId, healths ->
      new DefaultCacheData(instanceId, Collections.emptyMap(), [(HEALTH.ns): healths])
    }

    Set<String> dupeDetected = []
    Collection<CacheData> eurekaCacheData = eurekaInstances.values().findResults { List<CacheData> cacheDatas ->
      if (cacheDatas.size() == 1) {
        return cacheDatas[0]
      }

      cacheDatas.sort(new EurekaHealthComparator())
      def data = cacheDatas.first()
      dupeDetected.add(data.id)
      return data
    }
    if (dupeDetected) {
      log.warn("Duplicate eureka records found for instances: $dupeDetected")
    }
    log.info("Caching ${eurekaCacheData.size()} items in ${agentType}")
    new DefaultCacheResult(
      (INSTANCES.ns): instanceCacheData,
      (HEALTH.ns): eurekaCacheData)
  }

  private static class EurekaHealthComparator implements Comparator<CacheData> {
    @Override
    int compare(CacheData a, CacheData b) {
      return HealthState.fromString(a.attributes.state) <=> HealthState.fromString(b.attributes.state) ?:
        (Long) b.attributes.lastUpdatedTimestamp <=> (Long) a.attributes.lastUpdatedTimestamp
    }
  }

  @Override
  long getPollIntervalMillis() {
    return pollIntervalMillis
  }

  @Override
  long getTimeoutMillis() {
    return timeoutMillis
  }

}
