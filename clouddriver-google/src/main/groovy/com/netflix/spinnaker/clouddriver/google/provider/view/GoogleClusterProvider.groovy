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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.*
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
class GoogleClusterProvider implements ClusterProvider<GoogleCluster.View> {

  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  GoogleApplicationProvider applicationProvider
  @Autowired
  GoogleInstanceProvider instanceProvider
  @Autowired
  GoogleSecurityGroupProvider securityGroupProvider

  @Override
  Map<String, Set<GoogleCluster.View>> getClusters() {
    cacheView.getAll(CLUSTERS.ns).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<GoogleCluster.View>> getClusterDetails(String applicationName) {
    getClusters(applicationName, true /* detailed */)
  }

  @Override
  Map<String, Set<GoogleCluster.View>> getClusterSummaries(String applicationName) {
    getClusters(applicationName, false /* detailed */)
  }

  Map<String, Set<GoogleCluster.View>> getClusters(String applicationName, boolean includeInstanceDetails) {
    GoogleApplication.View application = applicationProvider.getApplication(applicationName)

    def clusterKeys = []
    application?.clusterNames?.each { String accountName, Set<String> clusterNames ->
      clusterNames.each { String clusterName ->
        clusterKeys << Keys.getClusterKey(accountName, applicationName, clusterName)
      }
    }

    List<GoogleCluster.View> clusters = cacheView.getAll(
        CLUSTERS.ns,
        clusterKeys,
        RelationshipCacheFilter.include(SERVER_GROUPS.ns)).collect { CacheData cacheData ->
      clusterFromCacheData(cacheData, includeInstanceDetails)
    }

    clusters?.groupBy { it.accountName } as Map<String, Set<GoogleCluster.View>>
  }

  @Override
  Set<GoogleCluster.View> getClusters(String applicationName, String account) {
    getClusterDetails(applicationName)?.get(account)
  }

  @Override
  GoogleCluster.View getCluster(String applicationName, String accountName, String clusterName) {
    CacheData clusterData = cacheView.get(
      CLUSTERS.ns,
      Keys.getClusterKey(accountName, applicationName, clusterName),
      RelationshipCacheFilter.include(SERVER_GROUPS.ns))

    return clusterData ? clusterFromCacheData(clusterData, true /* Include instance details */ ) : null
  }

  @Override
  GoogleServerGroup.View getServerGroup(String account, String region, String name) {
    def cacheData = cacheView.get(SERVER_GROUPS.ns,
                                  Keys.getServerGroupKey(name, account, region),
                                  RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))

    if (!cacheData) {
      // No regional server group was found, so attempt to query for all zonal server groups in the region.
      def pattern = Keys.getServerGroupKey(name, account, region, "*")
      def identifiers = cacheView.filterIdentifiers(SERVER_GROUPS.ns, pattern)
      def cacheDataResults = cacheView.getAll(SERVER_GROUPS.ns,
                                              identifiers,
                                              RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))

      if (cacheDataResults) {
        cacheData = cacheDataResults.first()
      }
    }

    if (cacheData) {
      return serverGroupFromCacheData(cacheData, account)?.view
    }
  }

  GoogleCluster.View clusterFromCacheData(CacheData cacheData, boolean includeInstanceDetails) {
    GoogleCluster.View clusterView = objectMapper.convertValue(cacheData.attributes, GoogleCluster)?.view

    def serverGroupKeys = cacheData.relationships[SERVER_GROUPS.ns]
    if (serverGroupKeys) {
      def filter = includeInstanceDetails ?
          RelationshipCacheFilter.include(LOAD_BALANCERS.ns, INSTANCES.ns) :
          RelationshipCacheFilter.include(LOAD_BALANCERS.ns)
      cacheView.getAll(SERVER_GROUPS.ns,
                       serverGroupKeys,
                       filter).each { CacheData serverGroupCacheData ->
        GoogleServerGroup serverGroup = serverGroupFromCacheData(serverGroupCacheData, clusterView.accountName)
        clusterView.serverGroups << serverGroup.view
        clusterView.loadBalancers.addAll(serverGroup.loadBalancers*.view)
      }
    }

    clusterView
  }

  GoogleServerGroup serverGroupFromCacheData(CacheData cacheData, String account) {
    GoogleServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes, GoogleServerGroup)

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    def loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).collect {
      def loadBalancer = null
      switch (it.attributes?.type) {
        case GoogleLoadBalancerType.HTTP.toString():
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleHttpLoadBalancer)
          break
        case GoogleLoadBalancerType.NETWORK.toString():
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleLoadBalancer)
          break
        default:
          loadBalancer = null
          break
      }
      serverGroup.loadBalancers << loadBalancer.view
      loadBalancer
    }

    Set<GoogleSecurityGroup> securityGroups = securityGroupProvider.getAll(false)
    serverGroup.securityGroups = GoogleSecurityGroupProvider.getMatchingServerGroupNames(
        account,
        securityGroups,
        serverGroup.instanceTemplateTags,
        serverGroup.networkName)

    def instanceKeys = cacheData.relationships[INSTANCES.ns]
    if (instanceKeys) {
      serverGroup.instances = instanceProvider.getInstances(account, instanceKeys as List, securityGroups) as Set
      serverGroup.instances.each { GoogleInstance instance ->
        def foundHealths = getLoadBalancerHealths(instance.name, loadBalancers)
        if (foundHealths) {
          instance.loadBalancerHealths = foundHealths
        }
      }
    }

    // Time to aggregate health states that can't be computed during the server group fetch operation.

    // Health states for L7 load balancer.
    def httpLoadBalancers = loadBalancers.findAll { it.type == GoogleLoadBalancerType.HTTP }
    def httpDisabledStates = httpLoadBalancers.collect { loadBalancer ->
        Utils.determineHttpLoadBalancerDisabledState(loadBalancer, serverGroup)
    }

    // Health states for Consul.
    def consulNodes = serverGroup.instances?.collect { it.consulNode } ?: []
    def consulRunning = ConsulProviderUtils.consulServerGroup(consulNodes)
    def consulDisabled = false
    if (consulRunning) {
      consulDisabled = ConsulProviderUtils.serverGroupDisabled(consulNodes)
    }

    if (httpDisabledStates && httpDisabledStates.size() == loadBalancers.size()) {
      // We have only L7.
      serverGroup.disabled = httpDisabledStates.every { it }
    } else if (httpDisabledStates) {
      // We have a mix of L4 and L7.
      serverGroup.disabled &= httpDisabledStates.every { it }
    }

    // Now that disabled is set based on L7 & L4 state, we need to take Consul into account.
    if (consulRunning) {
      // If there are no load balancers to determine enable/disabled status we rely on Consul exclusively.
      if (loadBalancers.size() == 0) {
          serverGroup.disabled = true
      }
      // If the server group is disabled, but Consul isn't, we say the server group is discoverable.
      // If the server group isn't disabled, but Consul is, we say the server group can be reached via load balancer.
      // If the server group and Consul are both disabled, the server group remains disabled.
      // If the server group and Consul are both not disabled, the server group is not disabled.
      serverGroup.disabled &= consulDisabled
      serverGroup.discovery = true
    }

    serverGroup
  }

  static List<GoogleLoadBalancerHealth> getLoadBalancerHealths(String instanceName, List<GoogleLoadBalancer> loadBalancers) {
    loadBalancers*.healths.findResults { List<GoogleLoadBalancerHealth> glbhs ->
      glbhs.findAll { GoogleLoadBalancerHealth glbh ->
        glbh.instanceName == instanceName
      }
    }.flatten()
  }
}
