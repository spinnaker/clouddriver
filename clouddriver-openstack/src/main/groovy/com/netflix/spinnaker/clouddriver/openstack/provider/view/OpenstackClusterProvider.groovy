/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackCluster
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.stream.Collectors

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

@Component
class OpenstackClusterProvider implements ClusterProvider<OpenstackCluster.View> {
  final OpenstackCloudProvider openstackCloudProvider
  final Cache cacheView
  final ObjectMapper objectMapper
  final Closure<String> clusterAccountMapper = { Cluster it -> it.accountName }
  final OpenstackInstanceProvider instanceProvider

  @Autowired
  OpenstackClusterProvider(final OpenstackCloudProvider openstackCloudProvider,
                           final Cache cacheView,
                           final ObjectMapper objectMapper,
                           final OpenstackInstanceProvider instanceProvider) {
    this.openstackCloudProvider = openstackCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
    this.instanceProvider = instanceProvider
  }

  @Override
  Map<String, Set<OpenstackCluster.View>> getClusters() {
    Map<String, Set<OpenstackCluster.View>> result = Collections.emptyMap()

    final Collection<CacheData> cacheResults = cacheView.getAll(CLUSTERS.ns)

    if (cacheResults) {
      result = cacheResults.stream().map { CacheData cacheData -> objectMapper.convertValue(cacheData.attributes, OpenstackCluster)?.view }
        .collect(Collectors.groupingBy(this.&clusterAccountMapper, Collectors.toSet()))
    }

    result
  }

  @Override
  Map<String, Set<OpenstackCluster.View>> getClusterSummaries(final String application) {
    getClustersInternal(application, false)
  }

  @Override
  Map<String, Set<OpenstackCluster.View>> getClusterDetails(final String application) {
    getClustersInternal(application, true)
  }

  @Override
  Set<OpenstackCluster.View> getClusters(final String application, final String account) {
    getClusterDetails(application)?.get(account)
  }

  @Override
  OpenstackCluster.View getCluster(String application, String account, String name, boolean includeDetails) {
    getClusters(application, account)?.find { it.name == name }
  }

  @Override
  OpenstackCluster.View getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true)
  }

  @Override
  OpenstackServerGroup.View getServerGroup(final String account, final String region, final String name) {
    ServerGroup result = null
    CacheData cacheData = cacheView.get(SERVER_GROUPS.ns, Keys.getServerGroupKey(name, account, region),
      RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))

    if (cacheData) {
      result = serverGroupFromCacheData(cacheData)
    }

    result
  }

  @Override
  String getCloudProviderId() {
    return openstackCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return false
  }

  protected Map<String, Set<OpenstackCluster.View>> getClustersInternal(
    final String applicationName, final boolean includeInstanceDetails) {
    Map<String, Set<OpenstackCluster.View>> result = null

    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application) {
      Collection<String> clusterKeys = application.relationships[CLUSTERS.ns]
      Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys, RelationshipCacheFilter.include(SERVER_GROUPS.ns))

      result = clusters.stream()
        .map { this.clusterFromCacheData(it, includeInstanceDetails) }
        .collect(Collectors.groupingBy(this.&clusterAccountMapper, Collectors.toSet()))
    }
    result
  }

  protected OpenstackCluster.View clusterFromCacheData(final CacheData cacheData, final boolean includeDetails = false) {
    OpenstackCluster.View openstackCluster = objectMapper.convertValue(cacheData.attributes, OpenstackCluster)?.view

    Collection<String> serverGroupKeys = cacheData.relationships[SERVER_GROUPS.ns]
    if (serverGroupKeys) {
      RelationshipCacheFilter filter = includeDetails ?
        RelationshipCacheFilter.include(LOAD_BALANCERS.ns, INSTANCES.ns) :
        RelationshipCacheFilter.include(LOAD_BALANCERS.ns)
      cacheView.getAll(SERVER_GROUPS.ns, serverGroupKeys, filter).each { CacheData serverGroupCacheData ->
        openstackCluster.serverGroups << serverGroupFromCacheData(serverGroupCacheData)
        openstackCluster.loadBalancers.addAll(loadBalancersFromCacheData(serverGroupCacheData))
      }
    }
    openstackCluster
  }

  protected OpenstackServerGroup.View serverGroupFromCacheData(final CacheData cacheData) {
    OpenstackServerGroup.View serverGroup = objectMapper.convertValue(cacheData.attributes, OpenstackServerGroup)?.view

    Collection<String> instanceKeys = cacheData.relationships[INSTANCES.ns]
    if (instanceKeys) {
      serverGroup.instances = instanceProvider.getInstances(instanceKeys)

      // Add zones from instances to server group
      serverGroup.zones = serverGroup?.instances?.collect { it.zone }?.toSet()
    }

    // Disabled status for Consul.
    def consulNodes = serverGroup.instances?.collect { it.consulNode } ?: []
    def consulDiscoverable = ConsulProviderUtils.consulServerGroupDiscoverable(consulNodes)
    if (consulDiscoverable) {
      // If the server group is disabled (members are disabled or there are no load balancers), but Consul isn't,
      //  we say the server group is disabled and discoverable.
      // If the server group isn't disabled, but Consul is, we say the server group is not disabled (can be reached via load balancer).
      // If the server group and Consul are both disabled, the server group remains disabled.
      // If the server group and Consul are both not disabled, the server group is not disabled.
      serverGroup.disabled &= ConsulProviderUtils.serverGroupDisabled(consulNodes)
      serverGroup.discovery = true
    }

    serverGroup
  }

  protected Set<OpenstackLoadBalancer.View> loadBalancersFromCacheData(final CacheData cacheData) {
    List<OpenstackLoadBalancer> result = []
    Collection<String> loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    if (loadBalancerKeys) {
      cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).collect {
        result << objectMapper.convertValue(it.attributes, OpenstackLoadBalancer)
      }
    }
    result.findResults { it.view }
  }
}
