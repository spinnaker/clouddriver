package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetwork
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetworkDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.NETWORKS


@Slf4j
@RestController
@Component
class TencentNetworkProvider implements NetworkProvider<TencentNetwork> {
  final String cloudProvider = TencentCloudProvider.ID
  final Cache cacheView
  final ObjectMapper objectMapper
  private final TencentInfrastructureProvider tencentProvider

  @Autowired
  TencentNetworkProvider(TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<TencentNetwork> getAll() {
    getAllMatchingKeyPattern(Keys.getNetworkKey('*', '*', '*'))
  }

  Set<TencentNetwork> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(NETWORKS.ns, pattern))
  }

  Set<TencentNetwork> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(NETWORKS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  TencentNetwork fromCacheData(CacheData cacheData) {
    TencentNetworkDescription vnet = objectMapper.convertValue(cacheData.attributes[NETWORKS.ns], TencentNetworkDescription)
    def parts = Keys.parse(cacheData.id)
    //log.info("TencentNetworkDescription id = ${cacheData.id}, parts = ${parts}")

    new TencentNetwork(
      id: vnet.vpcId,
      name: vnet.vpcName,
      cidrBlock: vnet.cidrBlock,
      isDefault: vnet.isDefault,
      account: parts.account?: "none",
      region: parts.region?: "none",
    )
  }
}
