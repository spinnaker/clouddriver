package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSubnet
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSubnetDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.SUBNETS



@Slf4j
@Component
class TencentSubnetProvider implements SubnetProvider<TencentSubnet> {
  final String cloudProvider = TencentCloudProvider.ID
  private final Cache cacheView
  final ObjectMapper objectMapper
  private final TencentInfrastructureProvider tencentProvider

  @Autowired
  TencentSubnetProvider(TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<TencentSubnet> getAll() {
    getAllMatchingKeyPattern(Keys.getSubnetKey('*', '*', '*'))
  }

  Set<TencentSubnet> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern))
  }

  Set<TencentSubnet> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  TencentSubnet fromCacheData(CacheData cacheData) {
    TencentSubnetDescription subnet = objectMapper.convertValue(cacheData.attributes[SUBNETS.ns], TencentSubnetDescription)
    def parts = Keys.parse(cacheData.id)

    new TencentSubnet(
      id: subnet.subnetId,
      name: subnet.subnetName,
      vpcId: subnet.vpcId,
      cidrBlock: subnet.cidrBlock,
      isDefault: subnet.isDefault,
      zone: subnet.zone,
      purpose: "",
      account: parts.account?: "unknown",
      region: parts.region?: "unknown"
    )
  }
}
