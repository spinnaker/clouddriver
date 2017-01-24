package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosInstanceProvider implements InstanceProvider<DcosInstance> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosInstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  final String cloudProvider = DcosCloudProvider.ID

  @Override
  DcosInstance getInstance(String account, String region, String name) {
    def realRegion = region == "root" ? "" : region

    Set<CacheData> instances = getAllMatchingKeyPattern(cacheView, Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, realRegion, name))
    if (!instances || instances.size() == 0) {
      return null
    }

//    if (instances.size() > 1) {
//      throw new IllegalStateException("Multiple kubernetes pods with name $name in namespace $namespace exist.")
//    }

    CacheData instanceData = (CacheData) instances.toArray()[0]

    if (!instanceData) {
      return null
    }

//    def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
//      Keys.parse(it).name
//    }

    // TODO must i do this?
    DcosInstance instance = objectMapper.convertValue(instanceData.attributes.instance, DcosInstance)
    //instance.loadBalancers = loadBalancers

    return instance
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }

  static Set<CacheData> getAllMatchingKeyPattern(Cache cacheView, String namespace, String pattern) {
    loadResults(cacheView, namespace, cacheView.filterIdentifiers(namespace, pattern))
  }

  private static Set<CacheData> loadResults(Cache cacheView, String namespace, Collection<String> identifiers) {
    cacheView.getAll(namespace, identifiers, RelationshipCacheFilter.none())
  }
}
