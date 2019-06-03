package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroup;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Component
@Slf4j
public class TencentSecurityGroupProvider implements SecurityGroupProvider<TencentSecurityGroup> {
  @Autowired
  public TencentSecurityGroupProvider(
      TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentSecurityGroup> getAll(final boolean includeRules) {
    log.info(
        "Enter TencentSecurityGroupProvider getAll,includeRules=" + String.valueOf(includeRules));
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", "*", "*"), includeRules);
  }

  @Override
  public Set<TencentSecurityGroup> getAllByRegion(final boolean includeRules, final String region) {
    log.info(
        "Enter TencentSecurityGroupProvider getAllByRegion,includeRules="
            + String.valueOf(includeRules)
            + ",region="
            + region);
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", "*", region), includeRules);
  }

  @Override
  public Set<TencentSecurityGroup> getAllByAccount(
      final boolean includeRules, final String account) {
    log.info(
        "Enter TencentSecurityGroupProvider getAllByAccount,includeRules="
            + String.valueOf(includeRules)
            + ",account="
            + account);
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", account, "*"), includeRules);
  }

  @Override
  public Set<TencentSecurityGroup> getAllByAccountAndName(
      final boolean includeRules, final String account, final String securityGroupName) {
    log.info(
        "Enter TencentSecurityGroupProvider getAllByAccountAndName,includeRules="
            + String.valueOf(includeRules)
            + ", account="
            + account
            + ",securityGroupName="
            + securityGroupName);
    return getAllMatchingKeyPattern(
        Keys.getSecurityGroupKey("*", securityGroupName, account, "*"), includeRules);
  }

  @Override
  public Set<TencentSecurityGroup> getAllByAccountAndRegion(
      final boolean includeRules, final String account, final String region) {
    log.info(
        "Enter TencentSecurityGroupProvider getAllByAccountAndRegion,includeRules="
            + String.valueOf(includeRules)
            + ", account="
            + account
            + ",region="
            + region);
    return getAllMatchingKeyPattern(
        Keys.getSecurityGroupKey("*", "*", account, region), includeRules);
  }

  @Override
  public TencentSecurityGroup get(
      final String account, final String region, final String securityGroupName, String other) {
    log.info(
        "Enter TencentSecurityGroupProvider get,account="
            + account
            + ",region="
            + region
            + ",securityGroupName="
            + securityGroupName);
    return ((TencentSecurityGroup)
        (getAllMatchingKeyPattern(
                Keys.getSecurityGroupKey("*", securityGroupName, account, region), true)
            .iterator()
            .next()));
  }

  public Set<TencentSecurityGroup> getAllMatchingKeyPattern(
      final String pattern, boolean includeRules) {
    log.info("Enter getAllMatchingKeyPattern pattern = " + pattern);
    return loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern));
  }

  public Set<TencentSecurityGroup> loadResults(
      boolean includeRules, Collection<String> identifiers) {
    Collection<CacheData> data =
        cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none());
    Set<TencentSecurityGroup> transformed =
        data.stream()
            .map(
                it -> {
                  return this.fromCacheData(includeRules, it);
                })
            .collect(Collectors.toSet());
    return transformed;
  }

  public TencentSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    // log.info("securityGroup cacheData =
    // ${cacheData.id},${cacheData.attributes[SECURITY_GROUPS.ns]}")
    TencentSecurityGroupDescription sg =
        objectMapper.convertValue(
            cacheData.getAttributes().get(SECURITY_GROUPS.ns),
            TencentSecurityGroupDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());
    Names names = Names.parseName(sg.getSecurityGroupName());

    final Optional<String> account = Optional.ofNullable(parts.get("account"));
    final Optional<String> app = Optional.ofNullable(names == null ? null : names.getApp());
    final Optional<String> region = Optional.ofNullable(parts.get("region"));
    return TencentSecurityGroup.builder()
        .id(sg.getSecurityGroupId())
        .name(sg.getSecurityGroupName())
        .description(sg.getSecurityGroupDesc())
        .accountName(account.orElse("none"))
        .application(app.orElse("none"))
        .region(region.orElse("none"))
        .inRules(sg.getInRules())
        .outRules(sg.getOutRules())
        .build();
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final Cache getCacheView() {
    return cacheView;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentInfrastructureProvider tencentProvider;
}
