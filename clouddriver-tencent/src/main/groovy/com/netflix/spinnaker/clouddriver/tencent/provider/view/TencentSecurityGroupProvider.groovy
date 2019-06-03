package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroup
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
@Component
class TencentSecurityGroupProvider implements SecurityGroupProvider<TencentSecurityGroup> {
  final String cloudProvider = TencentCloudProvider.ID
  final Cache cacheView
  final ObjectMapper objectMapper
  private final TencentInfrastructureProvider tencentProvider

  @Autowired
  TencentSecurityGroupProvider(TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<TencentSecurityGroup> getAll(boolean includeRules) {
    log.info("Enter TencentSecurityGroupProvider getAll,includeRules=${includeRules}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*','*', '*', '*'), includeRules)
  }

  @Override
  Set<TencentSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    log.info("Enter TencentSecurityGroupProvider getAllByRegion,includeRules=${includeRules},region=${region}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*','*', '*', region), includeRules)
  }

  @Override
  Set<TencentSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    log.info("Enter TencentSecurityGroupProvider getAllByAccount,includeRules=${includeRules},account=${account}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*','*', account, '*'), includeRules)
  }

  @Override
  Set<TencentSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String securityGroupName) {
    log.info("Enter TencentSecurityGroupProvider getAllByAccountAndName,includeRules=${includeRules}," +
      "account=${account},securityGroupName=${securityGroupName}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', securityGroupName, account,'*'), includeRules)
  }

  @Override
  Set<TencentSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    log.info("Enter TencentSecurityGroupProvider getAllByAccountAndRegion,includeRules=${includeRules}," +
      "account=${account},region=${region}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*','*', account, region), includeRules)
  }

  @Override
  TencentSecurityGroup get(String account, String region, String securityGroupName, String other) {
    log.info("Enter TencentSecurityGroupProvider get,account=${account},region=${region},securityGroupName=${securityGroupName}")
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', securityGroupName, account, region), true)[0]
  }

  Set<TencentSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    log.info("Enter getAllMatchingKeyPattern pattern = ${pattern}")
    loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  Set<TencentSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  TencentSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    //log.info("securityGroup cacheData = ${cacheData.id},${cacheData.attributes[SECURITY_GROUPS.ns]}")
    TencentSecurityGroupDescription sg = objectMapper.convertValue(cacheData.attributes[SECURITY_GROUPS.ns], TencentSecurityGroupDescription)
    def parts = Keys.parse(cacheData.id)
    def names = Names.parseName(sg.securityGroupName)

    new TencentSecurityGroup(
      id: sg.securityGroupId,
      name: sg.securityGroupName,
      description: sg.securityGroupDesc,
      accountName: parts.account?: "none",
      application: names?.app?: "none",
      region: parts.region?: "none",
      inboundRules: [],
      outboundRules: [],
      inRules: sg.inRules,
      outRules: sg.outRules
    )
  }

}
