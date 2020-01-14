package com.netflix.spinnaker.clouddriver.tencent.provider.view;

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
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class TencentSubnetProvider implements SubnetProvider<TencentSubnet> {
  @Autowired
  public TencentSubnetProvider(TencentInfrastructureProvider tCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
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
    Collection<CacheData> data = cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none());
    List<TencentSubnet> transformed = DefaultGroovyMethods.collect(data, (Closure<TencentSubnet>) new MethodClosure(this, "fromCacheData"));

    return ((Set<TencentSubnet>) (transformed));
  }

  public TencentSubnet fromCacheData(CacheData cacheData) {
    TencentSubnetDescription subnet = objectMapper.convertValue(cacheData.getAttributes().get(SUBNETS.ns), TencentSubnetDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());

    TencentSubnet subnet1 = new TencentSubnet();


    final String account = parts.account;

    final String region = parts.region;

    return subnet1.setId(subnet.getSubnetId()) subnet1.setName(subnet.getSubnetName())
    subnet1.setVpcId(subnet.getVpcId()) subnet1.setCidrBlock(subnet.getCidrBlock())
    subnet1.setIsDefault(subnet.getIsDefault()) subnet1.setZone(subnet.getZone()) subnet1.setPurpose("")
    subnet1.setAccount(StringGroovyMethods.asBoolean(account) ? account : "unknown")
    subnet1.setRegion(StringGroovyMethods.asBoolean(region) ? region : "unknown");
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
