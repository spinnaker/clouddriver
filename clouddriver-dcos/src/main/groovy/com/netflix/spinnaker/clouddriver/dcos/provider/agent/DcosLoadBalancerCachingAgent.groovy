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

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class DcosLoadBalancerCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {

  private final String accountName
  private final DcosCredentials credentials
  private final DCOS dcosClient
  private final DcosCloudProvider dcosCloudProvider = new DcosCloudProvider()
  private final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
          AUTHORITATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
          //INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns)
  ] as Set)

  DcosLoadBalancerCachingAgent(String accountName,
                               DcosCredentials credentials,
                               DcosClientProvider clientProvider,
                               ObjectMapper objectMapper,
                               Registry registry) {
    this.credentials = credentials
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(credentials)
    this.metricsSupport = new OnDemandMetricsSupport(registry,
            this,
            "$dcosCloudProvider.id:$OnDemandAgent.OnDemandType.LoadBalancer")
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  String getAccountName() {
    return accountName
  }

  @Override
  String getAgentType() {
    //return "${accountName}/${getSimpleName()}[${agentIndex + 1}/$agentCount]"
    "${accountName}/${DcosLoadBalancerCachingAgent.simpleName}"
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

    //reloadNamespaces()
    //String group = data.region
    //if (this.namespaces.contains(namespace)) {
    //  return null
    //}

    def dcosSpinnakerId = DcosSpinnakerId.parse(data.loadBalancerName.toString())

    App loadBalancer = metricsSupport.readData {
      loadLoadBalancer(dcosSpinnakerId)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([loadBalancer], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getLoadBalancerKey(dcosSpinnakerId)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
                Keys.getLoadBalancerKey(dcosSpinnakerId),
                10 * 60, // ttl is 10 minutes
                [
                        cacheTime     : System.currentTimeMillis(),
                        cacheResults  : jsonResult,
                        processedCount: 0,
                        processedTime : null
                ],
                [:]
        )

        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this load balancer if it no longer exists.
    Map<String, Collection<String>> evictions = loadBalancer ? [:] : [
            (Keys.Namespace.LOAD_BALANCERS.ns): [
                    Keys.getLoadBalancerKey(dcosSpinnakerId)
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
      if (parse && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
      [
              details       : Keys.parse(it.id),
              cacheTime     : it.attributes.cacheTime,
              processedCount: it.attributes.processedCount,
              processedTime : it.attributes.processedTime
      ]
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.LoadBalancer == type && cloudProvider == dcosCloudProvider.id
  }

  List<App> loadLoadBalancers() {

    // TODO Additional query params for label selector filtering don't work! remove it from marathon-client/
    // dcosClient.getApps(accountName, [:])

    dcosClient.getApps(accountName).apps.findAll { it.labels.containsKey("SPINNAKER_LOAD_BALANCER") }
  }

  App loadLoadBalancer(DcosSpinnakerId id) {

    // TODO could just use the SPINNAKER_LOAD_BALANCER label query?

    // Easier to work with nulls in groovy
    dcosClient.maybeApp(id.toString()).orElse(null);
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<App> loadBalancers = loadLoadBalancers()

    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
            loadBalancers.collect {
              def id = DcosSpinnakerId.parse(it.id)
              Keys.getLoadBalancerKey(id)
            }).each {
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        evictFromOnDemand << it
      } else {
        keepInOnDemand << it
      }
    }

    def result = buildCacheResult(loadBalancers, keepInOnDemand.collectEntries { CacheData onDemandEntry ->
      [(onDemandEntry.id): onDemandEntry]
    }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private
  static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
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

  private CacheResult buildCacheResult(List<App> loadBalancers, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    for (App loadBalancer : loadBalancers) {
      if (!loadBalancer) {
        continue
      }

      DcosSpinnakerId dcosSpinnakerId = DcosSpinnakerId.parse(loadBalancer.id)

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getLoadBalancerKey(dcosSpinnakerId)] : null

      if (onDemandData && onDemandData.attributes.cachetime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {
        })
        cache(cacheResults, Keys.Namespace.LOAD_BALANCERS.ns, cachedLoadBalancers)
      } else {
        def loadBalancerKey = Keys.getLoadBalancerKey(dcosSpinnakerId)

        cachedLoadBalancers[loadBalancerKey].with {
          attributes.name = dcosSpinnakerId.toString()
          attributes.app = loadBalancer
          // Relationships are stored in DcosServerGroupCachingAgent.
        }
      }
    }

    log.info("Caching ${cachedLoadBalancers.size()} load balancers in ${agentType}")

    new DefaultCacheResult([
            (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
            (Keys.Namespace.ON_DEMAND.ns)     : onDemandKeep.values()
    ], [
            (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])
  }
}

