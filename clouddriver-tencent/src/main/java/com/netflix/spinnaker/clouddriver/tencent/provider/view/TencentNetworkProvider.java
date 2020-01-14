package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.NetworkProvider;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetwork;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetworkDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@RestController
@Component
public class TencentNetworkProvider implements NetworkProvider<TencentNetwork> {
  @Autowired
  public TencentNetworkProvider(
      TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = tCloudProvider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentNetwork> getAll() {
    return getAllMatchingKeyPattern(Keys.getNetworkKey("*", "*", "*"));
  }

  public Set<TencentNetwork> getAllMatchingKeyPattern(String pattern) {
    return loadResults(cacheView.filterIdentifiers(NETWORKS.ns, pattern));
  }

  public Set<TencentNetwork> loadResults(Collection<String> identifiers) {
    Collection<CacheData> data =
        cacheView.getAll(NETWORKS.ns, identifiers, RelationshipCacheFilter.none());
    Set<TencentNetwork> transformed =
        data.stream().map(it -> this.fromCacheData(it)).collect(Collectors.toSet());
    return transformed;
  }

  public TencentNetwork fromCacheData(CacheData cacheData) {
    TencentNetworkDescription vnet =
        objectMapper.convertValue(
            cacheData.getAttributes().get(NETWORKS.ns), TencentNetworkDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());
    // log.info("TencentNetworkDescription id = ${cacheData.id}, parts = ${parts}")

    final String account = parts != null ? parts.get("account") : null;
    final String region = parts != null ? parts.get("region") : null;
    TencentNetwork network =
        TencentNetwork.builder()
            .id(vnet.getVpcId())
            .name(vnet.getVpcName())
            .cidrBlock(vnet.getCidrBlock())
            .isDefault(vnet.getIsDefault())
            .account(!StringUtils.isEmpty(account) ? account : "none")
            .region(!StringUtils.isEmpty(region) ? region : "none")
            .build();
    return network;
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
