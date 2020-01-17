package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentTargetHealth;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTargetHealth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class TencentInstanceProvider implements InstanceProvider<TencentInstance, String> {
  @Override
  public TencentInstance getInstance(final String account, final String region, String id) {
    String key = Keys.getInstanceKey(id, account, region);

    CacheData cacheData =
        cacheView
            .getAll(
                INSTANCES.ns,
                new ArrayList<String>(Arrays.asList(key)),
                RelationshipCacheFilter.include(LOAD_BALANCERS.ns, SERVER_GROUPS.ns, CLUSTERS.ns))
            .stream()
            .filter(it -> it != null)
            .findFirst()
            .orElse(null);
    if (cacheData != null) {
      return instanceFromCacheData(account, region, cacheData);
    }
    return null;
  }

  public TencentInstance instanceFromCacheData(String account, String region, CacheData cacheData) {
    TencentInstance instance =
        objectMapper.convertValue(cacheData.getAttributes().get("instance"), TencentInstance.class);

    String serverGroupName = instance.getServerGroupName();
    CacheData serverGroupCache =
        cacheView.get(SERVER_GROUPS.ns, Keys.getServerGroupKey(serverGroupName, account, region));
    final Map<String, Object> attributes =
        (serverGroupCache == null ? null : serverGroupCache.getAttributes());
    Map asgInfo = (attributes == null ? null : (Map) attributes.get("asg"));
    List<Map<String, Object>> lbInfos =
        asgInfo == null ? null : (List<Map<String, Object>>) asgInfo.get("forwardLoadBalancerSet");
    if (!CollectionUtils.isEmpty(lbInfos)) {
      for (Map<String, Object> lbInfo : lbInfos) {
        String lbId = (String) lbInfo.get("loadBalancerId");
        String listenerId = (String) lbInfo.get("listenerId");
        String lbHealthKey =
            Keys.getTargetHealthKey(lbId, listenerId, "", instance.getName(), account, region);
        CacheData lbHealthCache = cacheView.get(HEALTH_CHECKS.ns, lbHealthKey);
        final Map<String, Object> healthAttributres =
            (lbHealthCache == null ? null : lbHealthCache.getAttributes());
        TencentLoadBalancerTargetHealth loadBalancerTargetHealth =
            healthAttributres == null
                ? null
                : (TencentLoadBalancerTargetHealth) healthAttributres.get("targetHealth");
        if (loadBalancerTargetHealth != null) {
          TencentTargetHealth targetHealth =
              new TencentTargetHealth(loadBalancerTargetHealth.getHealthStatus());
          TencentTargetHealth.TargetHealthStatus healthStatus =
              targetHealth.getTargetHealthStatus();
          TencentTargetHealth.LBHealthSummary summary = new TencentTargetHealth.LBHealthSummary();
          summary.setLoadBalancerName(lbId);
          summary.setState(healthStatus.toServiceStatus());
          targetHealth.getLoadBalancers().add(summary);
          instance.getTargetHealths().add(targetHealth);
        } else {
          // if server group has lb, but can't get lb health check result for instance in server
          // group
          // assume the target health check result is UNKNOWN
          instance.getTargetHealths().add(new TencentTargetHealth());
        }
      }
    }

    return instance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TencentCloudProvider getTencentCloudProvider() {
    return tencentCloudProvider;
  }

  public void setTencentCloudProvider(TencentCloudProvider tencentCloudProvider) {
    this.tencentCloudProvider = tencentCloudProvider;
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }

  private final String cloudProvider = TencentCloudProvider.ID;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TencentCloudProvider tencentCloudProvider;
  @Autowired private Cache cacheView;
}
