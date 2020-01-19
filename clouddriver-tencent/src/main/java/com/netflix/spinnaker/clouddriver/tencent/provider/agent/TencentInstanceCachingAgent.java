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
import org.apache.logging.log4j.util.Strings;
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
    log.info("loadData, asgInstances = {}", Strings.join(asgInstances, ','));
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

    log.info("load instanceDetail reuslts {}", Strings.join(result, ','));

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
              TencentInstance tencentInstance = TencentInstance.builder().build();

              tencentInstance.setAccount(getAccountName());
              tencentInstance.setName(it.getInstanceId());
              tencentInstance.setInstanceName(it.getInstanceName());
              tencentInstance.setLaunchTime(launchTime != null ? launchTime.getTime() : 0);
              tencentInstance.setZone(it.getPlacement().getZone());
              tencentInstance.setVpcId(it.getVirtualPrivateCloud().getVpcId());
              tencentInstance.setSubnetId(it.getVirtualPrivateCloud().getSubnetId());
              tencentInstance.setPrivateIpAddresses(
                  Optional.ofNullable(it.getPrivateIpAddresses()).map(Arrays::asList).orElse(null));
              tencentInstance.setPublicIpAddresses(
                  Optional.ofNullable(it.getPublicIpAddresses()).map(Arrays::asList).orElse(null));
              tencentInstance.setImageId(it.getImageId());
              tencentInstance.setInstanceType(it.getInstanceType());
              tencentInstance.setSecurityGroupIds(
                  Optional.ofNullable(it.getSecurityGroupIds()).map(Arrays::asList).orElse(null));
              tencentInstance.setInstanceHealth(
                  TencentInstanceHealth.builder()
                      .instanceStatus(TencentInstanceHealth.Status.valueOf(it.getInstanceState()))
                      .build());
              tencentInstance.setServerGroupName(
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
                    Keys.getClusterKey(
                        moniker.getCluster(),
                        Optional.ofNullable(moniker.getApp()).orElse(""),
                        getAccountName());
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
    log.info("finish loads Tencent instance data.");
    // log.info("Caching " + namespaceCache.get(INSTANCES.ns) + " items in " + getAgentType());
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
