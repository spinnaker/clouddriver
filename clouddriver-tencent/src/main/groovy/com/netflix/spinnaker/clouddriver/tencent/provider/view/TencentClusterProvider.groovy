package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.netflix.discovery.converters.Auto
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.model.TencentCluster
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance
import com.netflix.spinnaker.clouddriver.tencent.model.TencentServerGroup
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*

@Slf4j
@Component
class TencentClusterProvider implements ClusterProvider<TencentCluster> {
  @Autowired
  TencentCloudProvider tencentCloudProvider

  @Autowired
  TencentInstanceProvider tencentInstanceProvider

  @Autowired
  Cache cacheView

  @Override
  Map<String, Set<TencentCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<TencentCluster> clusters = translateClusters(clusterData, false)
    clusters.groupBy { it.accountName }.collectEntries { k, v ->
      [k, new HashSet(v)]
    } as Map<String, Set<TencentCluster>>
  }

  @Override
  Map<String, Set<TencentCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(
      APPLICATIONS.ns,
      Keys.getApplicationKey(applicationName))

    if (application) {
      Collection<TencentCluster> clusters = translateClusters(
        resolveRelationshipData(application, CLUSTERS.ns),
        false)
      clusters.groupBy { it.accountName }.collectEntries { k, v ->
        [k, new HashSet(v)]
      } as Map<String, Set<TencentCluster>>
    } else {
      return null
    }
  }

  @Override
  Map<String, Set<TencentCluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(
      APPLICATIONS.ns,
      Keys.getApplicationKey(applicationName))

    if (application) {
      log.info("application is ${application.id}.")
      Collection<TencentCluster> clusters = translateClusters(
        resolveRelationshipData(application, CLUSTERS.ns),
        true)
      clusters.groupBy {
        it.accountName
      }.collectEntries { k, v ->
        [k, new HashSet(v)]
      } as Map<String, Set<TencentCluster>>
    } else {
      log.info("application is not found.")
      null
    }
  }

  @Override
  Set<TencentCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(
      APPLICATIONS.ns,
      Keys.getApplicationKey(applicationName),
      RelationshipCacheFilter.include(CLUSTERS.ns)
    )

    if (application) {
      Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll {
        Keys.parse(it).account == account
      }
      Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
      translateClusters(clusters, true) as Set<TencentCluster>
    } else {
      null
    }
  }

  @Override
  TencentCluster getCluster(
    String application, String account, String name, boolean includeDetails) {
    CacheData cluster = cacheView.get(
      CLUSTERS.ns,
      Keys.getClusterKey(name, application, account))

    cluster ? translateClusters([cluster], includeDetails)[0] : null
  }

  @Override
  TencentCluster getCluster(String applicationName, String accountName, String clusterName) {
    getCluster(applicationName, accountName, clusterName, true)
  }

  @Override
  TencentServerGroup getServerGroup(
    String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey name, account, region
    CacheData serverGroupData = cacheView.get SERVER_GROUPS.ns, serverGroupKey
    if (serverGroupData) {
      String imageId = serverGroupData.attributes.launchConfig["imageId"]
      CacheData imageConfig = imageId ? cacheView.get(
        IMAGES.ns,
        Keys.getImageKey(imageId, account, region)
      ) : null


      def serverGroup = new TencentServerGroup(serverGroupData.attributes)
      serverGroup.accountName = account
      serverGroup.image = imageConfig ? imageConfig.attributes.image as Map : null

      if (includeDetails) {
        // show instances info
        serverGroup.instances = getServerGroupInstances(account, region, serverGroupData)
      }
      serverGroup
    } else {
      null
    }
  }

  @Override
  TencentServerGroup getServerGroup(String account, String region, String name) {
    getServerGroup(account, region, name, true)
  }

  @Override
  String getCloudProviderId() {
    return tencentCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return true
  }

  String getServerGroupAsgId(String serverGroupName, String account, String region) {
    def serverGroup = getServerGroup(account, region, serverGroupName, false)
    serverGroup ? serverGroup.asg.autoScalingGroupId as String : null
  }

  private Collection<TencentCluster> translateClusters(
    Collection<CacheData> clusterData,
    boolean includeDetails) {

    // todo test lb detail
    Map<String, TencentLoadBalancer> loadBalancers
    Map<String, TencentServerGroup> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(
        clusterData,
        LOAD_BALANCERS.ns
      )
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(
        clusterData,
        SERVER_GROUPS.ns,
        RelationshipCacheFilter.include(INSTANCES.ns, LAUNCH_CONFIGS.ns)
      )
      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    } else {
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(
        clusterData,
        SERVER_GROUPS.ns,
        RelationshipCacheFilter.include(INSTANCES.ns)
      )
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<TencentCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      TencentCluster cluster = new TencentCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults {
        serverGroups.get(it)
      }

      if (includeDetails) {
        def lb = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.findResults {
          loadBalancers.get(it)
        }
        cluster.loadBalancers = lb
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new TencentLoadBalancer(
            id: parts.id,
            accountName: parts.account,
            region: parts.region
          )
        }
      }
      cluster
    }
    clusters
  }

  private static Map<String, TencentLoadBalancer> translateLoadBalancers(
    Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> lbKey = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id): new TencentLoadBalancer(
        id: lbKey.id, accountName: lbKey.account, region: lbKey.region)]
    }
  }

  private Map<String, TencentServerGroup> translateServerGroups(
    Collection<CacheData> serverGroupData) {
    Map<String, TencentServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      TencentServerGroup serverGroup = new TencentServerGroup(serverGroupEntry.attributes)

      def account = serverGroup.accountName
      def region = serverGroup.region

      serverGroup.instances = getServerGroupInstances(account, region, serverGroupEntry)

      String imageId = serverGroupEntry.attributes.launchConfig["imageId"]
      CacheData imageConfig = imageId ? cacheView.get(
        IMAGES.ns,
        Keys.getImageKey(imageId, account, region)
      ) : null

      serverGroup.image = imageConfig ? imageConfig.attributes.image as Map : null

      [(serverGroupEntry.id): serverGroup]
    }
    serverGroups
  }

  private Set<TencentInstance> getServerGroupInstances(String account, String region, CacheData serverGroupData) {
    def instanceKeys = serverGroupData.relationships[INSTANCES.ns]
    Collection<CacheData> instances = cacheView.getAll(
      INSTANCES.ns,
      instanceKeys
    )

    instances.collect {
      tencentInstanceProvider.instanceFromCacheData(account, region, it)
    }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(
    CacheData source,
    String relationship,
    Closure<Boolean> relFilter,
    CacheFilter cacheFilter = null) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships, cacheFilter) : []
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(
    Collection<CacheData> sources,
    String relationship,
    CacheFilter cacheFilter = null) {

    Collection<String> relationships = sources?.findResults {
      it.relationships[relationship] ?: []
    }?.flatten() ?: []

    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }
}
