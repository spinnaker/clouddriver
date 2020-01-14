package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.model.NamespaceCache;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstanceHealth;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.cvm.v20170312.models.Tag;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class TencentInstanceCachingAgent extends AbstractTencentCachingAgent {
  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    // first, find all auto scaling instances
    // second, get detail info of below instances
    log.info("start load auto scaling instance data");

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    final NamespaceCache namespaceCache = new NamespaceCache();

    AutoScalingClient asClient =
        new AutoScalingClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());
    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());

    final List<Instance> asgInstances = asClient.getAutoScalingInstances();
    final List<String> asgInstanceIds =
        asgInstances.stream()
            .map(
                it -> {
                  return it.getInstanceId();
                })
            .collect(Collectors.toList());

    log.info("loads " + asgInstanceIds.size() + " auto scaling instances. ");

    log.info("start load instances detail info.");
    List<com.tencentcloudapi.cvm.v20170312.models.Instance> result =
        cvmClient.getInstances(asgInstanceIds);

    result.stream()
        .forEach(
            it -> {
              Date launchTime = CloudVirtualMachineClient.ConvertIsoDateTime(it.getCreatedTime());
              final String launchConfigurationName =
                  asgInstances.stream()
                      .filter(
                          asgIns -> {
                            return asgIns.getInstanceId().equals(it.getInstanceId());
                          })
                      .findFirst()
                      .map(Instance::getLaunchConfigurationName)
                      .orElse(null);

              final String serverGroupName =
                  Arrays.stream(it.getTags())
                      .filter(
                          tag -> {
                            return tag.getKey()
                                .equals(AutoScalingClient.getDefaultServerGroupTagKey());
                          })
                      .findFirst()
                      .map(Tag::getValue)
                      .orElse(null);
              TencentInstance instance = TencentInstance.builder().build();

              Map<String, Object> map = new HashMap<String, Object>(1);
              map.put("instanceStatus", it.getInstanceState());

              final TencentInstance tencentInstance = instance.setAccount(getAccountName());
              instance.setName(it.getInstanceId());
              instance.setInstanceName(it.getInstanceName());
              instance.setLaunchTime(launchTime != null ? launchTime.getTime() : 0);
              instance.setZone(it.getPlacement().getZone());
              instance.setVpcId(it.getVirtualPrivateCloud().getVpcId());
              instance.setSubnetId(it.getVirtualPrivateCloud().getSubnetId());
              instance.setPrivateIpAddresses(Arrays.asList(it.getPrivateIpAddresses()));
              instance.setPublicIpAddresses(Arrays.asList(it.getPublicIpAddresses()));
              instance.setImageId(it.getImageId());
              instance.setInstanceType(it.getInstanceType());
              instance.setSecurityGroupIds(Arrays.asList(it.getSecurityGroupIds()));
              instance.setInstanceHealth(
                  TencentInstanceHealth.builder()
                      .instanceStatus(TencentInstanceHealth.Status.valueOf(it.getInstanceState()))
                      .build());
              instance.setServerGroupName(
                  !StringUtils.isEmpty(serverGroupName)
                      ? serverGroupName
                      : launchConfigurationName);

              if (!ArrayUtils.isEmpty(it.getTags())) {
                Arrays.stream(it.getTags())
                    .forEach(
                        tag -> {
                          tencentInstance
                              .getTags()
                              .add(
                                  new HashMap<String, String>() {
                                    {
                                      put("key", tag.getKey());
                                      put("value", tag.getValue());
                                    }
                                  });
                        });
              }

              Map<String, CacheData> instances = namespaceCache.get(INSTANCES.ns);
              String instanceKey =
                  Keys.getInstanceKey(
                      it.getInstanceId(),
                      TencentInstanceCachingAgent.this.getAccountName(),
                      TencentInstanceCachingAgent.this.getRegion());

              instances.get(instanceKey).getAttributes().put("instance", tencentInstance);

              Moniker moniker = tencentInstance.getMoniker();
              if (moniker != null) {
                String clusterKey =
                    Keys.getClusterKey(moniker.getCluster(), moniker.getApp(), getAccountName());
                String serverGroupKey =
                    Keys.getServerGroupKey(
                        tencentInstance.getServerGroupName(), getAccountName(), getRegion());
                instances.get(instanceKey).getRelationships().get(CLUSTERS.ns).add(clusterKey);
                instances
                    .get(instanceKey)
                    .getRelationships()
                    .get(SERVER_GROUPS.ns)
                    .add(serverGroupKey);
              }
            });

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> {
          cacheResults.put(namespace, cacheDataMap.values());
        });

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults);
    log.info("finish loads instance data.");
    log.info("Caching " + namespaceCache.get(INSTANCES.ns) + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public TencentInstanceCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    super(credentials, objectMapper, region);
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(INSTANCES.ns));
          add(INFORMATIVE.forType(SERVER_GROUPS.ns));
          add(INFORMATIVE.forType(CLUSTERS.ns));
        }
      };
}
