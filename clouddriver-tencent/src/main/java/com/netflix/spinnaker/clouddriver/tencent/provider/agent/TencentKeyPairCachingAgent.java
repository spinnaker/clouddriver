package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.KEY_PAIRS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.model.NamespaceCache;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentKeyPair;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.KeyPair;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentKeyPairCachingAgent extends AbstractTencentCachingAgent {
  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("start load key pair data");

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    final NamespaceCache namespaceCache = new NamespaceCache();

    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());

    List<KeyPair> result = cvmClient.getKeyPairs();
    result.stream()
        .forEach(
            it -> {
              TencentKeyPair tencentKeyPair =
                  TencentKeyPair.builder()
                      .keyId(it.getKeyId())
                      .keyName(it.getKeyName())
                      .keyFingerprint("")
                      .region(TencentKeyPairCachingAgent.this.getRegion())
                      .account(TencentKeyPairCachingAgent.this.getAccountName())
                      .build();

              Map<String, CacheData> keyPairs = namespaceCache.get(KEY_PAIRS.ns);
              String keyPairKey =
                  Keys.getKeyPairKey(
                      tencentKeyPair.getKeyName(),
                      TencentKeyPairCachingAgent.this.getAccountName(),
                      TencentKeyPairCachingAgent.this.getRegion());
              keyPairs.get(keyPairKey).getAttributes().put("keyPair", tencentKeyPair);
            });

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> {
          cacheResults.put(namespace, cacheDataMap.values());
        });

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults);
    log.info("finish loads key pair data.");
    log.info(
        "Caching "
            + String.valueOf(namespaceCache.get(KEY_PAIRS.ns).size())
            + " items in "
            + getAgentType());
    return defaultCacheResult;
  }

  public TencentKeyPairCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    super(credentials, objectMapper, region);
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          AUTHORITATIVE.forType(KEY_PAIRS.ns);
        }
      };
}
