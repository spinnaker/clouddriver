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
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleApplication
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
  GoogleCluster.View getCluster(String application, String account, String name) {
    getClusters(application, account).find { it.name == name }
  }

  @Override
  GoogleServerGroup.View getServerGroup(String account, String region, String name) {
    def cacheData = cacheView.get(SERVER_GROUPS.ns,
                                  Keys.getServerGroupKey(name, account, region),
                                  RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))
    if (cacheData) {
      return serverGroupFromCacheData(cacheData)?.view
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
        GoogleServerGroup serverGroup = serverGroupFromCacheData(serverGroupCacheData)
        clusterView.serverGroups << serverGroup.view
        clusterView.loadBalancers.addAll(serverGroup.loadBalancers*.view)
      }
    }

    clusterView
  }

  GoogleServerGroup serverGroupFromCacheData(CacheData cacheData) {
    GoogleServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes, GoogleServerGroup)

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    List<GoogleLoadBalancer> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).collect {
      GoogleLoadBalancer loadBalancer = objectMapper.convertValue(it.attributes, GoogleLoadBalancer)
      serverGroup.loadBalancers << loadBalancer
      loadBalancer
    }

    Set<GoogleSecurityGroup> securityGroups = securityGroupProvider.getAll(false)
    serverGroup.securityGroups = GoogleSecurityGroupProvider.getMatchingServerGroupNames(
        securityGroups,
        serverGroup.instanceTemplateTags,
        serverGroup.networkName)

    def instanceKeys = cacheData.relationships[INSTANCES.ns]
    if (instanceKeys) {
      serverGroup.instances = instanceProvider.getInstances(instanceKeys as List, securityGroups) as Set
      serverGroup.instances.each { GoogleInstance instance ->
        def foundHealths = getLoadBalancerHealths(instance.name, loadBalancers)
        if (foundHealths) {
          instance.loadBalancerHealths = foundHealths
        }
      }
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
