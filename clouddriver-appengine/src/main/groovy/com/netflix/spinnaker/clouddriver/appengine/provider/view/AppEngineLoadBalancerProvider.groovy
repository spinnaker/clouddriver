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

package com.netflix.spinnaker.clouddriver.appengine.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.AppEngineConfiguration
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AppEngineLoadBalancerProvider implements LoadBalancerProvider<AppEngineLoadBalancer> {

  final String cloudProvider = AppEngineCloudProvider.ID

  @Override
  List<LoadBalancerProvider.Item> list() {
    // TODO(danielpeach): Implement.
    throw new UnsupportedOperationException("AppEngine Not Yet Ready.")
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    throw new UnsupportedOperationException("AppEngine Not Yet Ready.")
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account, String region, String name) {
    throw new UnsupportedOperationException("AppEngine Not Yet Ready.")
  }

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Override
  Set<AppEngineLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String applicationKey = Keys.getApplicationKey(applicationName)
    CacheData application = cacheView.get(Namespace.APPLICATIONS.ns, applicationKey)

    def applicationLoadBalancers = AppEngineProviderUtils.resolveRelationshipData(cacheView,
                                                                                  application,
                                                                                  Namespace.LOAD_BALANCERS.ns)
    translateLoadBalancers(applicationLoadBalancers)
  }

  Set<AppEngineLoadBalancer> translateLoadBalancers(Collection<CacheData> cacheData) {
    cacheData.collect { loadBalancerData ->

      Set<AppEngineServerGroup> serverGroups = AppEngineProviderUtils
        .resolveRelationshipData(cacheView, loadBalancerData, Namespace.SERVER_GROUPS.ns)
        .collect {
          Set<AppEngineInstance> instances = AppEngineProviderUtils
            .resolveRelationshipData(cacheView, it, Namespace.INSTANCES.ns)
            .findResults { AppEngineProviderUtils.instanceFromCacheData(objectMapper, it) }
          AppEngineProviderUtils.serverGroupFromCacheData(objectMapper, it, instances)
        }

      AppEngineProviderUtils.loadBalancerFromCacheData(objectMapper, loadBalancerData, serverGroups)
    }
  }

  AppEngineLoadBalancer getLoadBalancer(String account, String loadBalancerName) {
    String loadBalancerKey = Keys.getLoadBalancerKey(account, loadBalancerName)
    CacheData loadBalancerData = cacheView.get(Namespace.LOAD_BALANCERS.ns, loadBalancerKey)
    Set<AppEngineLoadBalancer> loadBalancerSet = translateLoadBalancers([loadBalancerData] - null)

    loadBalancerSet ? loadBalancerSet.first() : null
  }
}
