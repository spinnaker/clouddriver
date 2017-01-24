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

package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.NameValidation
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosLoadBalancer
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import mesosphere.marathon.client.model.v2.App
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.naming.OperationNotSupportedException

@Component
class DcosLoadBalancerProvider implements LoadBalancerProvider<DcosLoadBalancer> {

  final String cloudProvider = DcosCloudProvider.ID

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  static String combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source?.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  @Override
  Set<DcosLoadBalancer> getApplicationLoadBalancers(String applicationName) {

    String applicationKey = Keys.getApplicationKey(applicationName)
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, applicationKey)

    Set<String> loadBalancerKeys = []
    Set<String> instanceKeys = []

    def applicationServerGroups = application ? resolveRelationshipData(cacheView, application, Keys.Namespace.SERVER_GROUPS.ns, {
      true
    }) : []
    applicationServerGroups.each { CacheData serverGroup ->
      loadBalancerKeys.addAll(serverGroup.relationships[Keys.Namespace.LOAD_BALANCERS.ns] ?: [])
    }

    loadBalancerKeys.addAll(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns,
            Keys.getLoadBalancerKey("*", combineAppStackDetail(applicationName, '*', null))))
    loadBalancerKeys.addAll(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns,
            Keys.getLoadBalancerKey("*", combineAppStackDetail(applicationName, null, null))))

    def loadBalancers = cacheView.getAll(Keys.Namespace.LOAD_BALANCERS.ns, loadBalancerKeys)

    Set<CacheData> allServerGroups = resolveRelationshipDataForCollection(cacheView, loadBalancers, Keys.Namespace.SERVER_GROUPS.ns)
//    allServerGroups.each { CacheData serverGroup ->
//      instanceKeys.addAll(serverGroup.relationships[Keys.Namespace.INSTANCES.ns] ?: [])
//    }
//
//    def instances = cacheView.getAll(Keys.Namespace.INSTANCES.ns, instanceKeys)
//    def instanceMap = KubernetesProviderUtils.controllerToInstanceMap(objectMapper, instances)

    Map<String, DcosServerGroup> serverGroupMap = allServerGroups.collectEntries { serverGroupData ->
      //def ownedInstances = instanceMap[(String) serverGroupData.attributes.name]
      def serverGroup = serverGroupFromCacheData(objectMapper, serverGroupData)
      return [(serverGroupData.id): serverGroup]
    }

    return loadBalancers.collect {
      translateLoadBalancer(it, serverGroupMap)
    } as Set
  }

  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship] ?: [] }?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static DcosServerGroup serverGroupFromCacheData(ObjectMapper objectMapper, CacheData cacheData) {
    DcosServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes.serverGroup, DcosServerGroup)
    return serverGroup
  }

  private DcosLoadBalancer translateLoadBalancer(CacheData loadBalancerEntry,
                                                 Map<String, DcosServerGroup> serverGroupMap) {
    App app = objectMapper.convertValue(loadBalancerEntry.attributes.app, App)

    List<DcosServerGroup> serverGroups = []
    loadBalancerEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.each { String serverGroupKey ->
      DcosServerGroup serverGroup = serverGroupMap[serverGroupKey]
      if (serverGroup) {
        serverGroups << serverGroup
      }
    }

    return new DcosLoadBalancer(app, 'global', serverGroups)
  }

//  private KubernetesLoadBalancer translateLoadBalancer(CacheData loadBalancerEntry, Map<String, KubernetesServerGroup> serverGroupMap) {
//    def parts = Keys.parse(loadBalancerEntry.id)
//    Service service = objectMapper.convertValue(loadBalancerEntry.attributes.service, Service)
//    List<KubernetesServerGroup> serverGroups = []
//    List<String> securityGroups
//    loadBalancerEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.forEach { String serverGroupKey ->
//      KubernetesServerGroup serverGroup = serverGroupMap[serverGroupKey]
//      if (serverGroup) {
//        serverGroups << serverGroup
//      }
//      return
//    }
//
//    securityGroups = KubernetesProviderUtils.resolveRelationshipData(cacheView, loadBalancerEntry, Keys.Namespace.SECURITY_GROUPS.ns).findResults { cacheData ->
//      if (cacheData.id) {
//        def parse = Keys.parse(cacheData.id)
//        parse ? parse.name : null
//      } else {
//        null
//      }
//    }
//
//    return new KubernetesLoadBalancer(service, serverGroups, parts.account, securityGroups)
//  }

  // TODO(lwander): Groovy allows this to compile just fine, even though KubernetesLoadBalancer does
  // not implement the LoadBalancerProvider.list interface.
  @Override
  List<DcosLoadBalancer> list() {
//    Collection<String> loadBalancers = cacheView.getIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns)
//    loadBalancers.findResults {
//      def parse = Keys.parse(it)
//      parse ? new KubernetesLoadBalancer(parse.name, parse.namespace, parse.account) : null
//    }
    return null
  }

  // TODO(lwander): Implement if/when these methods are needed in Deck.
  @Override
  LoadBalancerProvider.Item get(String name) {
    throw new OperationNotSupportedException("Kubernetes is a special snowflake.")
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account,
                                                               String region,
                                                               String name) {
    throw new OperationNotSupportedException("No balancers for you!")
  }
}
