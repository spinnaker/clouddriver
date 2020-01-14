package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSubnet;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSubnetDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TencentSubnetProvider implements SubnetProvider<TencentSubnet> {
  @Autowired
  public TencentSubnetProvider(
      TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentSubnet> getAll() {
    return getAllMatchingKeyPattern(Keys.getSubnetKey("*", "*", "*"));
  }

  public Set<TencentSubnet> getAllMatchingKeyPattern(String pattern) {
    return loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern));
  }

  public Set<TencentSubnet> loadResults(Collection<String> identifiers) {
    Collection<CacheData> data =
        cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none());
    Set<TencentSubnet> transformed =
        data.stream().map(it -> this.fromCacheData(it)).collect(Collectors.toSet());
    return transformed;
  }

  public TencentSubnet fromCacheData(CacheData cacheData) {
    TencentSubnetDescription subnet =
        objectMapper.convertValue(
            cacheData.getAttributes().get(SUBNETS.ns), TencentSubnetDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());
    final String account = parts.get("account");
    final String region = parts.get("region");
    return TencentSubnet.builder()
        .id(subnet.getSubnetId())
        .name(subnet.getSubnetName())
        .vpcId(subnet.getVpcId())
        .cidrBlock(subnet.getCidrBlock())
        .isDefault(subnet.getIsDefault())
        .zone(subnet.getZone())
        .purpose("")
        .account(!StringUtils.isEmpty(account) ? account : "unknown")
        .region(!StringUtils.isEmpty(region) ? region : "unknown")
        .build();
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentInfrastructureProvider tencentProvider;
}
