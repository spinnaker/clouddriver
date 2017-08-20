/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesServerGroup
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spectator.api.Registry
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Event
import io.kubernetes.client.models.V1beta1StatefulSet

/**
 * Created by spinnaker on 20/8/17.
 */
@Slf4j
class KubernetesControllersCachingAgent extends KubernetesCachingAgent implements OnDemandAgent{
  final String category = 'serverGroup'
  final OnDemandMetricsSupport metricsSupport

  KubernetesControllersCachingAgent(String accountName, ObjectMapper objectMapper, KubernetesCredentials credentials, int agentIndex, int agentCount, Registry registry) {
    super(accountName, objectMapper, credentials, agentIndex, agentCount)
    this.metricsSupport = new OnDemandMetricsSupport(registry,
      this,
      "$kubernetesCloudProvider.id:$OnDemandAgent.OnDemandType.ServerGroup")
  }

  @Override
  String getOnDemandAgentType() {
    return "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandMetricsSupport getMetricsSupport() {
    return null
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    return false
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    reloadNamespaces()
    String namespace = data.region
    if (!namespaces.contains(namespace)) {
      return null
    }

    def serverGroupName = data.serverGroupName.toString()

    V1beta1StatefulSet statefulSet = metricsSupport.readData {
      loadStatefulSets()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([new StateFulSet(statefulSet: statefulSet)], [:], [], Long.MAX_VALUE)
    }
    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getServerGroupKey(accountName, namespace, serverGroupName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getServerGroupKey(accountName, namespace, serverGroupName),
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
    // Evict this server group if it no longer exists.
    Map<String, Collection<String>> evictions = statefulSet  ? [:] : [
      (Keys.Namespace.SERVER_GROUPS.ns): [
        Keys.getServerGroupKey(accountName, namespace, serverGroupName)
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
    return null
  }

  /**
   * @return the data types this Agent returns
   * @see com.netflix.spinnaker.cats.agent.AgentDataType.Authority
   */
  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return KubernetesServerGroupCachingAgent.types
  }

  /**
   * Triggered by an AgentScheduler to tell this Agent to load its data.
   *
   * @param providerCache Cache associated with this Agent's provider
   * @return the complete set of data for this Agent.
   */
  @Override
  CacheResult loadData(ProviderCache providerCache) {
    reloadNamespaces()
    Long start = System.currentTimeMillis()
    List<V1beta1StatefulSet> statefulSet = loadStatefulSets()
    List<StateFulSet> serverGroups = (statefulSet.collect {
      it ? new StateFulSet(statefulSet: it) : null
    }
    ) - null
    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
      serverGroups.collect { serverGroup ->
        Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroup.name)
      })
      .each { CacheData onDemandEntry ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def result = buildCacheResult(serverGroups, keepInOnDemand.collectEntries { CacheData onDemandEntry ->
      [(onDemandEntry.id): onDemandEntry]
    }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  @Override
  String getSimpleName() {
    return KubernetesControllersCachingAgent.simpleName
  }

  List<V1beta1StatefulSet> loadStatefulSets() {
    namespaces.collect { String namespace ->
      credentials.apiClientAdaptor.getStatefulSets(namespace)
    }.flatten()
  }

  private CacheResult buildCacheResult(List<StateFulSet> serverGroups, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    Map<String, Map<String, Event>> stateFulsetEvents = [:].withDefault { _ -> [:] }

    try {
      namespaces.each { String namespace ->
        stateFulsetEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, "V1beta1StatefulSet")

      }
    } catch (Exception e) {
      log.warn "Failure fetching events for all server groups in $namespaces", e
    }

    for (StateFulSet serverGroup: serverGroups) {
      if (!serverGroup.exists()) {
        continue
      }

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroup.name)] : null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
          new TypeReference<Map<String, List<MutableCacheData>>>() { })
        cache(cacheResults, Keys.Namespace.APPLICATIONS.ns, cachedApplications)
        cache(cacheResults, Keys.Namespace.CLUSTERS.ns, cachedClusters)
        cache(cacheResults, Keys.Namespace.SERVER_GROUPS.ns, cachedServerGroups)
        cache(cacheResults, Keys.Namespace.INSTANCES.ns, cachedInstances)
      } else {
        def serverGroupName = serverGroup.name
        def names = Names.parseName(serverGroupName)
        def applicationName = names.app
        def clusterName = names.cluster
        def serverGroupKey = Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroupName)
        def applicationKey = Keys.getApplicationKey(applicationName)
        def clusterKey = Keys.getClusterKey(accountName, applicationName, category, clusterName)
        def instanceKeys = []

        cachedApplications[applicationKey].with {
          attributes.name = applicationName
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
        }

        cachedClusters[clusterKey].with {
          attributes.name = clusterName
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
        }

        cachedServerGroups[serverGroupKey].with {
          def events = null
          attributes.name = serverGroupName

          if (serverGroup.statefulSet) {

            events = stateFulsetEvents[serverGroup.namespace][serverGroupName]
          }
          attributes.serverGroup = new KubernetesServerGroup(serverGroup.statefulSet, accountName, events)
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
        }
      }
    }

    log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.APPLICATIONS.ns): cachedApplications.values(),
      (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (Keys.Namespace.CLUSTERS.ns): cachedClusters.values(),
      (Keys.Namespace.SERVER_GROUPS.ns): cachedServerGroups.values(),
      (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
      (Keys.Namespace.ON_DEMAND.ns): onDemandKeep.values()
    ],[
      (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])

  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String cacheNamespace, Map<String, CacheData> cacheDataById) {
    cacheResults[cacheNamespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (existingCacheData) {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      } else {
        cacheDataById[it.id] = it
      }
    }
  }

  class StateFulSet{

    V1beta1StatefulSet statefulSet
    String getName() {
      statefulSet.metadata.name
    }

    String getNamespace() {
      statefulSet.metadata.namespace
    }

    Map<String, String> getSelector() {
      statefulSet.spec.selector.matchLabels
    }

    boolean exists() {
      statefulSet
    }
  }
}
