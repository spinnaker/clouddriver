package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.NETWORKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetworkDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.vpc.v20170312.models.Vpc;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class TencentNetworkCachingAgent implements CachingAgent, AccountAware {
  public TencentNetworkCachingAgent(
      TencentNamedAccountCredentials creds, ObjectMapper objectMapper, String region) {
    this.accountName = creds.getName();
    this.credentials = creds;
    this.objectMapper = objectMapper;
    this.region = region;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in " + getAgentType());

    Set<TencentNetworkDescription> networks = loadNetworksAll();

    final List<CacheData> data =
        networks.stream()
            .map(
                network -> {
                  Map<String, Object> attributes =
                      new HashMap<String, Object>() {
                        {
                          put(NETWORKS.ns, network);
                        }
                      };
                  return new DefaultCacheData(
                      Keys.getNetworkKey(network.getVpcId(), accountName, region),
                      attributes,
                      new HashMap<>());
                })
            .collect(Collectors.toList());

    log.info("Caching " + data.size() + " items in " + getAgentType());
    return new DefaultCacheResult(
        new HashMap<String, Collection<CacheData>>() {
          {
            put(NETWORKS.ns, data);
          }
        });
  }

  private Set<TencentNetworkDescription> loadNetworksAll() {
    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);

    List<Vpc> networkSet = vpcClient.getNetworksAll(); // vpc

    return networkSet.stream()
        .map(
            it -> {
              TencentNetworkDescription networkDesc =
                  TencentNetworkDescription.builder()
                      .vpcId(it.getVpcId())
                      .vpcName(it.getVpcName())
                      .cidrBlock(it.getCidrBlock())
                      .isDefault(it.getIsDefault())
                      .build();
              return networkDesc;
            })
        .collect(Collectors.toSet());
  }

  public final String getAccountName() {
    return accountName;
  }

  public final String getProviderName() {
    return providerName;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final ObjectMapper objectMapper;
  private final String region;
  private final String accountName;
  private final TencentNamedAccountCredentials credentials;
  private final String providerName = TencentInfrastructureProvider.class.getName();
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(NETWORKS.ns));
        }
      };
}
