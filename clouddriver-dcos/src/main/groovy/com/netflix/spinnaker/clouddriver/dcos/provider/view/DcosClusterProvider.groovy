package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosCluster
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @author Will Gorman
 */
@Component
class DcosClusterProvider implements ClusterProvider<DcosCluster>{
  private final DcosCloudProvider dcosCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosClusterProvider(DcosCloudProvider dcosCloudProvider,
    Cache cacheView,
    ObjectMapper objectMapper) {
    this.dcosCloudProvider = dcosCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }
  @Override
  Map<String, Set<DcosCluster>> getClusters() {
    return [:] //TODO
  }

  @Override
  Map<String, Set<DcosCluster>> getClusterSummaries(final String application) {
    return [:] //TODO
  }

  @Override
  Map<String, Set<DcosCluster>> getClusterDetails(final String application) {
    return [:] //TODO
  }

  @Override
  Set<DcosCluster> getClusters(final String application, final String account) {
    return [] as Set //TODO
  }

  @Override
  DcosCluster getCluster(final String application, final String account, final String name) {
    //TODO
    return new DcosCluster().with {
      it.name = name
      accountName = account
    }
  }

  @Override
  ServerGroup getServerGroup(final String account, final String region, final String name) {
    String serverGroupKey = Keys.getServerGroupKey(DcosSpinnakerId.from(account, region, name))
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)
    if (!serverGroupData) {
      return null
    }

    objectMapper.convertValue(serverGroupData.attributes.serverGroup, DcosServerGroup)

  }

  @Override
  String getCloudProviderId() {
    return dcosCloudProvider.id
  }
}
