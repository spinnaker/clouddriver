package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.INSTANCE_TYPES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.model.NamespaceCache;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstanceType;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.InstanceTypeConfig;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentInstanceTypeCachingAgent extends AbstractTencentCachingAgent {
  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("start load instance types data");

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    final NamespaceCache namespaceCache = new NamespaceCache();

    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());

    InstanceTypeConfig[] result = cvmClient.getInstanceTypes();
    Arrays.stream(result)
        .forEach(
            it -> {
              TencentInstanceType tencentInstanceType =
                  TencentInstanceType.builder()
                      .name(it.getInstanceType())
                      .account(this.getAccountName())
                      .region(this.getRegion())
                      .zone(it.getZone())
                      .instanceFamily(it.getInstanceFamily())
                      .cpu(it.getCPU())
                      .mem(it.getMemory())
                      .build();

              Map<String, CacheData> instanceTypes = namespaceCache.get(INSTANCE_TYPES.ns);
              String instanceTypeKey =
                  Keys.getInstanceTypeKey(
                      TencentInstanceTypeCachingAgent.this.getAccountName(),
                      TencentInstanceTypeCachingAgent.this.getRegion(),
                      tencentInstanceType.getName());

              instanceTypes
                  .get(instanceTypeKey)
                  .getAttributes()
                  .put("instanceType", tencentInstanceType);
            });

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> {
          cacheResults.put(namespace, cacheDataMap.values());
        });

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults);
    log.info("finish loads instance type data.");
    log.info(
        "Caching " + namespaceCache.get(INSTANCE_TYPES.ns).size() + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public TencentInstanceTypeCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    super(credentials, objectMapper, region);
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(INSTANCE_TYPES.ns));
        }
      };
}
