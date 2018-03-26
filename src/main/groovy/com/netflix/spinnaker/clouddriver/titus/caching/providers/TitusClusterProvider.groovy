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

package com.netflix.spinnaker.clouddriver.titus.caching.providers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.core.provider.agent.ExternalHealthProvider
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.model.TitusCluster
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import com.netflix.spinnaker.clouddriver.titus.model.TitusServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*

@Component
class TitusClusterProvider implements ClusterProvider<TitusCluster> {

  private final TitusCloudProvider titusCloudProvider
  private final Cache cacheView
  private final TitusCachingProvider titusCachingProvider
  private final ObjectMapper objectMapper

  @Autowired
  AwsLookupUtil awsLookupUtil

  @Autowired
  TitusClusterProvider(TitusCloudProvider titusCloudProvider,
                       TitusCachingProvider titusCachingProvider,
                       Cache cacheView,
                       ObjectMapper objectMapper) {
    this.titusCloudProvider = titusCloudProvider
    this.cacheView = cacheView
    this.titusCachingProvider = titusCachingProvider
    this.objectMapper = objectMapper
  }

  @Autowired(required = false)
  List<ExternalHealthProvider> externalHealthProviders

  /**
   *
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<TitusCluster> clustersList = translateClusters(clusterData, false)
    Map<String, Set<TitusCluster>> clusters = clustersList.groupBy {
      it.accountName
    }.collectEntries { k, v -> [k, new HashSet(v)] }
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusterSummaries(String applicationName) {
    Map<String, Set<TitusCluster>> clusters = getClustersInternal(applicationName, false)
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusterDetails(String applicationName) {
    Map<String, Set<TitusCluster>> clusters = getClustersInternal(applicationName, true)
    clusters
  }

  /**
   *
   * @param applicationName
   * @param account
   * @return
   */
  @Override
  Set<TitusCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName),
      RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll {
      Keys.parse(it).account == account
    }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<TitusCluster>
  }

  /**
   *
   * @param application
   * @param account
   * @param name
   * @return
   */
  @Override
  TitusCluster getCluster(String application, String account, String name, boolean includeDetails) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    TitusCluster titusCluster = cluster ? translateClusters([cluster], includeDetails)[0] : null
    titusCluster
  }

  @Override
  TitusCluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true)
  }

  /**
   *
   * @param account
   * @param region
   * @param name
   * @return
   */
  @Override
  TitusServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }
    String json = objectMapper.writeValueAsString(serverGroupData.attributes.job)
    Job job = objectMapper.readValue(json, Job)
    TitusServerGroup serverGroup = new TitusServerGroup(job, serverGroupData.attributes.account, serverGroupData.attributes.region)
    serverGroup.placement.account = account
    serverGroup.placement.region = region
    serverGroup.scalingPolicies = serverGroupData.attributes.scalingPolicies
    serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns)).values()
    serverGroup.targetGroups = serverGroupData.attributes.targetGroups
    serverGroup
  }

  @Override
  String getCloudProviderId() {
    return titusCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return true
  }

  // Private methods
  private Map<String, Set<TitusCluster>> getClustersInternal(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) return null
    Collection<TitusCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  /**
   * Translate clusters
   */
  private Collection<TitusCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    def relationshipFilter = includeDetails ? RelationshipCacheFilter.include(INSTANCES.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, relationshipFilter)
    Map<String, TitusServerGroup> serverGroups = translateServerGroups(allServerGroups)

    Collection<TitusCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      TitusCluster cluster = new TitusCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      cluster
    }
    clusters
  }

  /**
   * Translate server groups
   */
  private Map<String, TitusServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns, RelationshipCacheFilter.none())
    Map<String, TitusInstance> instances = translateInstances(allInstances)

    Map<String, TitusServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      String json = objectMapper.writeValueAsString(serverGroupEntry.attributes.job)
      Job job = objectMapper.readValue(json, Job)
      TitusServerGroup serverGroup = new TitusServerGroup(job, serverGroupEntry.attributes.account, serverGroupEntry.attributes.region)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) } as Set

      if (!serverGroup.instances && serverGroupEntry.attributes.tasks) {
        // has no direct instance relationships but we can partially populate instances based on attributes.tasks
        serverGroup.instances = serverGroupEntry.attributes.tasks.collect {
          new TitusInstance(it as Map)
        }
      }
      serverGroup.targetGroups = serverGroupEntry.attributes.targetGroups
      serverGroup.instances = serverGroup.instances ?: []

      [(serverGroupEntry.id): serverGroup]
    }
    serverGroups
  }

  /**
   * Translate instances
   */
  private Map<String, TitusInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, TitusInstance> instances = instanceData.collectEntries { instanceEntry ->
      Job.TaskSummary task = objectMapper.convertValue(instanceEntry.attributes.task, Job.TaskSummary)
      Job job = objectMapper.convertValue(instanceEntry.attributes.job, Job)
      TitusInstance instance = new TitusInstance(job, task)
      instance.health = instanceEntry.attributes[HEALTH.ns]
      [(instanceEntry.id): instance]
    }

    // Adding health to instances
    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      externalHealthProviders.each { externalHealthProvider ->
        externalHealthProvider.agents.each { externalHealthAgent ->
          def key = Keys.getInstanceHealthKey(instanceEntry.attributes.task.instanceId, externalHealthAgent.healthId)
          healthKeysToInstance.put(key, instanceEntry.id)
        }
      }
    }
    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      healthEntry.attributes.remove('lastUpdatedTimestamp')
      instances[instanceId].health << healthEntry.attributes
    }

    instances
  }

  // Resolving cache data relationships

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources.findResults { it.relationships[relationship] ?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

}
