package com.netflix.spinnaker.clouddriver.tencent.provider.view;

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
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@Component
public class TencentNetworkProvider implements NetworkProvider<TencentNetwork> {
  @Autowired
  public TencentNetworkProvider(TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
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
    Collection<CacheData> data = cacheView.getAll(NETWORKS.ns, identifiers, RelationshipCacheFilter.none());
    List<TencentNetwork> transformed = DefaultGroovyMethods.collect(data, (Closure<TencentNetwork>) new MethodClosure(this, "fromCacheData"));

    return ((Set<TencentNetwork>) (transformed));
  }

  public TencentNetwork fromCacheData(CacheData cacheData) {
    TencentNetworkDescription vnet = objectMapper.convertValue(cacheData.getAttributes().get(NETWORKS.ns), TencentNetworkDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());
    //log.info("TencentNetworkDescription id = ${cacheData.id}, parts = ${parts}")

    TencentNetwork network = new TencentNetwork();


    final String account = parts.account;

    final String region = parts.region;

    return network.setId(vnet.getVpcId()) network.setName(vnet.getVpcName()) network.setCidrBlock(vnet.getCidrBlock())
    network.setIsDefault(vnet.getIsDefault())
    network.setAccount(StringGroovyMethods.asBoolean(account) ? account : "none")
    network.setRegion(StringGroovyMethods.asBoolean(region) ? region : "none");
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
