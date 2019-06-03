package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTargetHealth;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.clb.v20180317.models.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class TencentLoadBalancerInstanceStateCachingAgent
    implements CachingAgent, HealthProvidingCachingAgent, AccountAware {
  public TencentLoadBalancerInstanceStateCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    this.credentials = credentials;
    this.accountName = credentials.getName();
    this.region = region;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getHealthId() {
    return healthId;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter loadData in " + getAgentType());

    List<TencentLoadBalancerTargetHealth> targetHealths = getLoadBalancerTargetHealth();
    final Collection<String> evictions =
        providerCache.filterIdentifiers(
            HEALTH_CHECKS.ns, Keys.getTargetHealthKey("*", "*", "*", "*", accountName, region));

    final List<CacheData> data =
        targetHealths.stream()
            .map(
                targetHealth -> {
                  Map<String, Object> attributes = new HashMap<>(1);
                  attributes.put("targetHealth", targetHealth);
                  final String targetHealthKey =
                      Keys.getTargetHealthKey(
                          targetHealth.getLoadBalancerId(), targetHealth.getListenerId(),
                          targetHealth.getLocationId(), targetHealth.getInstanceId(),
                          getAccountName(), getRegion());
                  String keepKey =
                      evictions.stream()
                          .filter(it -> it.equals(targetHealthKey))
                          .findFirst()
                          .orElse(null);
                  if (keepKey != null) {
                    evictions.remove(keepKey);
                  }
                  return new DefaultCacheData(targetHealthKey, attributes, new HashMap<>());
                })
            .collect(Collectors.toList());

    log.info(
        "Caching "
            + data.size()
            + " items evictions "
            + evictions.size()
            + " items in "
            + getAgentType());
    return new DefaultCacheResult(
        new HashMap<String, Collection<CacheData>>() {
          {
            put(HEALTH_CHECKS.ns, data);
          }
        },
        new HashMap<String, Collection<String>>() {
          {
            put(HEALTH_CHECKS.ns, evictions);
          }
        });
  }

  private List<TencentLoadBalancerTargetHealth> getLoadBalancerTargetHealth() {
    LoadBalancerClient client =
        new LoadBalancerClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);

    List<LoadBalancer> loadBalancerSet = client.getAllLoadBalancer();
    List<String> loadBalancerIds =
        loadBalancerSet.stream().map(it -> it.getLoadBalancerId()).collect(Collectors.toList());

    final int totalLBCount = loadBalancerIds.size();
    log.info("Total loadBalancer Count " + totalLBCount);

    List<LoadBalancerHealth> targetHealthSet = new ArrayList();
    if (totalLBCount > 0) {
      targetHealthSet = client.getLBTargetHealth(loadBalancerIds);
    }

    List<TencentLoadBalancerTargetHealth> tencentLBTargetHealths =
        new ArrayList<TencentLoadBalancerTargetHealth>();
    for (LoadBalancerHealth lbHealth : targetHealthSet) {
      String loadBalancerId = lbHealth.getLoadBalancerId();
      ListenerHealth[] listenerHealths = lbHealth.getListeners();
      for (ListenerHealth listenerHealth : listenerHealths) {
        String listenerId = listenerHealth.getListenerId();
        RuleHealth[] ruleHealths = listenerHealth.getRules();
        String protocol = listenerHealth.getProtocol();
        for (RuleHealth ruleHealth : ruleHealths) {
          String locationId = "";
          if (protocol.equals("HTTP") || protocol.equals("HTTPS")) {
            locationId = ruleHealth.getLocationId();
          }
          TargetHealth[] instanceHealths = ruleHealth.getTargets();
          for (TargetHealth instanceHealth : instanceHealths) {
            String targetId = instanceHealth.getTargetId();
            Boolean healthStatus = instanceHealth.getHealthStatus();
            Integer port = instanceHealth.getPort();
            TencentLoadBalancerTargetHealth health =
                TencentLoadBalancerTargetHealth.builder()
                    .instanceId(targetId)
                    .loadBalancerId(loadBalancerId)
                    .listenerId(listenerId)
                    .locationId(locationId)
                    .healthStatus(healthStatus)
                    .port(port)
                    .build();

            tencentLBTargetHealths.add(health);
          }
        }
      }
    }

    return tencentLBTargetHealths;
  }

  private final String providerName = TencentInfrastructureProvider.class.getName();
  private TencentNamedAccountCredentials credentials;
  private final String accountName;
  private final String region;
  private final ObjectMapper objectMapper;
  private static final String healthId = "tencent-load-balancer-instance-health";
}
