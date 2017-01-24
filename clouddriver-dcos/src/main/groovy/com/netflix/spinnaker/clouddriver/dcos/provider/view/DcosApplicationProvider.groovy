package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosApplication
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosApplicationProvider implements ApplicationProvider {
  private final DcosCloudProvider dcosCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosApplicationProvider(DcosCloudProvider dcosCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.dcosCloudProvider = dcosCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(Keys.Namespace.APPLICATIONS.ns, cacheView.filterIdentifiers(Keys.Namespace.APPLICATIONS.ns, "${dcosCloudProvider.id}:*"), relationships)
    applications.collect this.&translate
  }

  @Override
  Application getApplication(String name) {
    translate(cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(name)))
  }

  Application translate(CacheData cacheData) {
    if (cacheData == null) {
      return null
    }

    String name = Keys.parse(cacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, DcosApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[Keys.Namespace.CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.name) {
        clusterNames[cluster.account].add(cluster.name)
      }
    }

    new DcosApplication(name, attributes, clusterNames)
  }
}
