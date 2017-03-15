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
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

/**
 * @author Will Gorman
 */
@Slf4j
class DcosServerGroupCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {
  private final String accountName
  private final DCOS dcosClient
  private final DcosCloudProvider dcosCloudProvider = new DcosCloudProvider()
  private final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
          AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
          AUTHORITATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
          AUTHORITATIVE.forType(Keys.Namespace.CLUSTERS.ns),
          INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns),
          INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
  ] as Set)

  DcosServerGroupCachingAgent(String accountName,
                              DcosCredentials credentials,
                              DcosClientProvider clientProvider,
                              ObjectMapper objectMapper,
                              Registry registry) {
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(credentials)
    this.metricsSupport = new OnDemandMetricsSupport(registry,
            this,
            "$dcosCloudProvider.id:$OnDemandAgent.OnDemandType.ServerGroup")

  }

  @Override
  String getAgentType() {
    return "${accountName}/${DcosServerGroupCachingAgent.simpleName}"
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
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(final ProviderCache providerCache) {
    Long start = System.currentTimeMillis()

    def serverGroups = loadServerGroups()

    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
            serverGroups.collect { serverGroup ->
              Keys.getServerGroupKey(new DcosSpinnakerAppId(serverGroup.app.id, accountName))
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

  // OnDemandAgent methods

  @Override
  String getOnDemandAgentType() {
    return "${getAgentType()}-OnDemand"
  }

  @Override
  boolean handles(final OnDemandAgent.OnDemandType type, final String cloudProvider) {
    return OnDemandAgent.OnDemandType.ServerGroup == type && cloudProvider == dcosCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(final ProviderCache providerCache, final Map<String, ?> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    def serverGroupName = data.serverGroupName.toString()

    def spinnakerId = new DcosSpinnakerAppId(accountName, data.region.toString(), serverGroupName)
    DcosServerGroup serverGroup = metricsSupport.readData {
      loadServerGroup(spinnakerId.toString())
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([serverGroup], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getServerGroupKey(spinnakerId)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
                Keys.getServerGroupKey(spinnakerId),
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

    // Evict this server group if it no longer exists.
    Map<String, Collection<String>> evictions = serverGroup ? [:] : [
            (Keys.Namespace.SERVER_GROUPS.ns): [
                    Keys.getServerGroupKey(spinnakerId)
            ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
            sourceAgentType: getOnDemandAgentType(),
            cacheResult: result,
            evictions: evictions //TODO
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(final ProviderCache providerCache) {
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

  // PRIVATE METHODS

  private DcosServerGroup loadServerGroup(String dcosAppId) {
    App app = dcosClient.getApp(dcosAppId)?.app
    app ? new DcosServerGroup(accountName, app) : null
  }

  private List<DcosServerGroup> loadServerGroups() {
    final List<App> apps = dcosClient.getApps(accountName)?.apps
    apps.findAll {
      !it.labels?.containsKey("SPINNAKER_LOAD_BALANCER") && DcosSpinnakerAppId.from(it.id, accountName).isPresent()
    }.collect {
      new DcosServerGroup(accountName, it)
    }
  }

  private CacheResult buildCacheResult(List<DcosServerGroup> serverGroups,
                                       Map<String, CacheData> onDemandKeep,
                                       List<String> onDemandEvict,
                                       Long start) {
    Map<String, MutableCacheData> cachedApps = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    serverGroups.each { serverGroup ->
      if (serverGroup == null) {
        return
      }

      def app = serverGroup.app
      def onDemandData = onDemandKeep ?
              onDemandKeep[Keys.getServerGroupKey(new DcosSpinnakerAppId(app.id, accountName))] :
              null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
                new TypeReference<Map<String, List<MutableCacheData>>>() {
                })
        cache(cacheResults, Keys.Namespace.APPLICATIONS.ns, cachedApps)
        cache(cacheResults, Keys.Namespace.CLUSTERS.ns, cachedClusters)
        cache(cacheResults, Keys.Namespace.SERVER_GROUPS.ns, cachedServerGroups)
        cache(cacheResults, Keys.Namespace.INSTANCES.ns, cachedInstances)

      } else {

        // based on the way kubernetes handles this it looks like the app id must conform to
        // the spinnaker naming convention as we have to parse it.  There's no storage that maps
        // an arbitrary app id to these fields
        def appId = app.id
        def spinnakerId = new DcosSpinnakerAppId(appId, accountName)
        def names = spinnakerId.getServerGroupName()
        def appName = names.app
        def clusterName = names.cluster
        def instanceKeys = []

        def loadBalancerKeys = serverGroup.fullyQualifiedLoadBalancers.findResults({
          Keys.getLoadBalancerKey(it)
        })

        def applicationKey = Keys.getApplicationKey(appName)
        def serverGroupKey = Keys.getServerGroupKey(spinnakerId)
        String clusterKey = Keys.getClusterKey(accountName, appName, clusterName)

        cachedApps[applicationKey].with {
          attributes.name = appName
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        cachedClusters[clusterKey].with {
          attributes.name = clusterName
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        app.tasks.forEach { task ->
          String instanceKey = Keys.getInstanceKey(spinnakerId, task.id)
          instanceKeys << instanceKey
          cachedInstances[instanceKey].with {
            relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
            relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          }
        }

        loadBalancerKeys.forEach { loadBalancerKey ->
          cachedLoadBalancers[loadBalancerKey].with {
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
          }
        }

        cachedServerGroups[serverGroupKey].with {
          attributes.name = appId
          attributes.serverGroup = serverGroup
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
        }

      }
    }

    log.info("Caching ${cachedApps.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
            (Keys.Namespace.SERVER_GROUPS.ns) : cachedServerGroups.values(),
            (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
            (Keys.Namespace.CLUSTERS.ns)      : cachedClusters.values(),
            (Keys.Namespace.SERVER_GROUPS.ns) : cachedServerGroups.values(),
            (Keys.Namespace.INSTANCES.ns)     : cachedInstances.values(),
            (Keys.Namespace.APPLICATIONS.ns)  : cachedApps.values(),
            (Keys.Namespace.ON_DEMAND.ns)     : onDemandKeep.values()],
            [(Keys.Namespace.ON_DEMAND.ns): onDemandEvict])
  }

  private
  static void cache(Map<String, List<CacheData>> cacheResults, String cacheNamespace, Map<String, CacheData> cacheDataById) {
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
}
