/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Service

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesLoadBalancerCachingAgent extends KubernetesCachingAgent implements OnDemandAgent {

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
    INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  KubernetesLoadBalancerCachingAgent(String accountName,
                                     KubernetesV1Credentials credentials,
                                     ObjectMapper objectMapper,
                                     int agentIndex,
                                     int agentCount,
                                     Registry registry) {
    super(accountName, objectMapper, credentials, agentIndex, agentCount)
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "$kubernetesCloudProvider.id:$OnDemandAgent.OnDemandType.LoadBalancer")
  }


  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    reloadNamespaces()
    String namespace = data.region
    if (this.namespaces.contains(namespace)) {
      return null
    }

    def loadBalancerName = data.loadBalancerName.toString()

    Service service = metricsSupport.readData {
      loadService(namespace, loadBalancerName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([service], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getLoadBalancerKey(accountName, namespace, loadBalancerName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getLoadBalancerKey(accountName, namespace, loadBalancerName),
          10 * 60, // ttl is 10 minutes
          [
            cacheTime: System.currentTimeMillis(),
            cacheResults: jsonResult,
            processedCount: 0,
            processedTime: null
          ],
          [:]
        )

        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this load balancer if it no longer exists.
    Map<String, Collection<String>> evictions = service ? [:] : [
      (Keys.Namespace.LOAD_BALANCERS.ns): [
        Keys.getLoadBalancerKey(accountName, namespace, loadBalancerName)
      ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && namespaces.contains(parse.namespace) && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
      [
        details: Keys.parse(it.id),
        cacheTime: it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime: it.attributes.processedTime
      ]
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.LoadBalancer == type && cloudProvider == kubernetesCloudProvider.id
  }

  List<Service> loadServices() {
    namespaces.collect { String namespace ->
      credentials.apiAdaptor.getServices(namespace)
    }.flatten() - null
  }

  Service loadService(String namespace, String name) {
    credentials.apiAdaptor.getService(namespace, name)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Service> services = loadServices()

    def evictFromOnDemand = []
    def keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
      services.collect { Keys.getLoadBalancerKey(accountName, it.metadata.namespace, it.metadata.name) }).each {
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        evictFromOnDemand << it
      } else {
        keepInOnDemand << it
      }
    }

    def result = buildCacheResult(services, keepInOnDemand.collectEntries { [(it.id): it] }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
    cacheResults[namespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }

  private CacheResult buildCacheResult(List<Service> services, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    for (Service service : services) {
      if (!service) {
        continue
      }

      def namespace = service.metadata.namespace
      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getLoadBalancerKey(accountName, namespace, service.metadata.name)] : null

      if (onDemandData && onDemandData.attributes.cachetime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() { })
        cache(cacheResults, Keys.Namespace.LOAD_BALANCERS.ns, cachedLoadBalancers)
      } else {
        def serviceName = service.metadata.name
        def loadBalancerKey = Keys.getLoadBalancerKey(accountName, namespace, serviceName)

        cachedLoadBalancers[loadBalancerKey].with {
          attributes.name = serviceName
          attributes.service = service
          // Relationships are stored in KubernetesServerGroupCachingAgent.
        }
      }
    }

    log.info("Caching ${cachedLoadBalancers.size()} load balancers in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (Keys.Namespace.ON_DEMAND.ns): onDemandKeep.values()
    ],[
      (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])

  }

  @Override
  String getSimpleName() {
    KubernetesLoadBalancerCachingAgent.simpleName
  }
}

