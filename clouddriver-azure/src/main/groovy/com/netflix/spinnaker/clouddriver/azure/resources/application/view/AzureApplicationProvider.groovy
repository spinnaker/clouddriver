package com.netflix.spinnaker.clouddriver.azure.resources.application.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.application.model.AzureApplication
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureApplicationProvider implements ApplicationProvider {
  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AzureApplicationProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    def relationships = expand ? RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> applications = cacheView.getAll(
      Keys.Namespace.APPLICATIONS.ns, cacheView.filterIdentifiers(Keys.Namespace.APPLICATIONS.ns, "${azureCloudProvider.id}:*"), relationships
    )
    applications.collect this.&translate
  }

  @Override
  Application getApplication(String name) {
    Map<String, String> attributes = [:]
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    // TODO:
    // translate(cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(azureCloudProvider, name)))

    new AzureApplication(name, attributes, clusterNames)
  }

  Application translate(CacheData cacheData) {
    if (cacheData == null) {
      return null
    }

    String name = Keys.parse(cacheData.id).application
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, AzureApplication.ATTRIBUTES)
    Map<String, Set<String>> clusterNames = [:].withDefault { new HashSet<String>() }
    for (String clusterId : cacheData.relationships[Keys.Namespace.CLUSTERS.ns]) {
      Map<String, String> cluster = Keys.parse(clusterId)
      if (cluster.account && cluster.cluster) {
        clusterNames[cluster.account].add(cluster.cluster)
      }
    }
    new AzureApplication(name, attributes, clusterNames)
  }

}
