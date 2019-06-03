package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroup;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

@Data
@Slf4j
public class TencentSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {
  public TencentSecurityGroupCachingAgent(
      TencentNamedAccountCredentials creds,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    this.accountName = creds.getName();
    this.credentials = creds;
    this.region = region;
    this.objectMapper = objectMapper;
    this.registry = registry;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            TencentCloudProvider.ID + ":" + String.valueOf(OnDemandType.SecurityGroup));
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.SecurityGroup) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(
      final ProviderCache providerCache, final Map<String, ? extends Object> data) {
    log.info("Enter TencentSecurityGroupCachingAgent handle, params = " + StringUtils.join(data));
    if (!data.containsKey("securityGroupId")
        || !data.containsKey("account")
        || !data.containsKey("region")
        || !accountName.equals(data.get("account"))
        || !region.equals(data.get("region"))) {
      log.info("TencentSecurityGroupCachingAgent: input params error!");
      return null;
    }

    final TencentSecurityGroupDescription evictedSecurityGroup =
        TencentSecurityGroupDescription.builder().build();
    final String securityGroupId = (String) data.get("securityGroupId");

    final TencentSecurityGroupDescription updatedSecurityGroup =
        metricsSupport.readData(() -> loadSecurityGroupById(securityGroupId));

    if (updatedSecurityGroup == null) {
      log.info(
          "TencentSecurityGroupCachingAgent: Can not find securityGroup "
              + securityGroupId
              + " in "
              + getRegion());
      return null;
    }

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> {
              if (updatedSecurityGroup != null) {
                return buildCacheResult(providerCache, null, 0, updatedSecurityGroup, null);
              } else {
                evictedSecurityGroup
                    .setSecurityGroupId(securityGroupId)
                    .setSecurityGroupName("unknown")
                    .setSecurityGroupDesc("unknown")
                    .setLastReadTime(System.currentTimeMillis());
                return buildCacheResult(providerCache, null, 0, null, evictedSecurityGroup);
              }
            });

    Map<String, Collection<String>> evictions =
        evictedSecurityGroup != null
            ? new HashMap<String, Collection<String>>() {
              {
                put(
                    SECURITY_GROUPS.ns,
                    new ArrayList<String>() {
                      {
                        add(
                            Keys.getSecurityGroupKey(
                                evictedSecurityGroup.getSecurityGroupId(),
                                evictedSecurityGroup.getSecurityGroupName(),
                                accountName,
                                region));
                      }
                    });
              }
            }
            : new HashMap<>();

    log.info(
        "TencentSecurityGroupCachingAgent: onDemand cache refresh (data: "
            + StringUtils.join(data)
            + ", evictions: "
            + StringUtils.join(evictions)
            + ")");
    OnDemandResult result = new OnDemandResult();
    result.setSourceAgentType(getAgentType());
    result.setCacheResult(cacheResult);
    result.setEvictions(evictions);
    return result;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter TencentSecurityGroupCachingAgent loadData in " + getAgentType());
    long currentTime = System.currentTimeMillis();
    final Set<TencentSecurityGroupDescription> securityGroupDescSet = loadSecurityGroupAll();

    log.info(
        "Total SecurityGroup Number = " + securityGroupDescSet.size() + " in " + getAgentType());
    return buildCacheResult(providerCache, securityGroupDescSet, currentTime, null, null);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new ArrayList<Map>();
  }

  private Set<TencentSecurityGroupDescription> loadSecurityGroupAll() {
    final VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);

    List<SecurityGroup> securityGroupSet = vpcClient.getSecurityGroupsAll();

    Set<TencentSecurityGroupDescription> securityGroupDescriptionSet =
        securityGroupSet.stream()
            .map(
                it -> {
                  SecurityGroupPolicySet securityGroupRules =
                      vpcClient.getSecurityGroupPolicies(it.getSecurityGroupId());

                  TencentSecurityGroupDescription securityGroupDesc =
                      TencentSecurityGroupDescription.builder()
                          .securityGroupId(it.getSecurityGroupId())
                          .securityGroupName(it.getSecurityGroupName())
                          .securityGroupDesc(it.getSecurityGroupDesc())
                          .inRules(
                              Arrays.stream(securityGroupRules.getIngress())
                                  .map(
                                      ingress -> {
                                        TencentSecurityGroupRule inRule =
                                            TencentSecurityGroupRule.builder()
                                                .index(ingress.getPolicyIndex())
                                                .protocol(ingress.getProtocol())
                                                .port(ingress.getPort())
                                                .cidrBlock(ingress.getCidrBlock())
                                                .action(ingress.getAction())
                                                .build();
                                        return inRule;
                                      })
                                  .collect(Collectors.toList()))
                          .outRules(
                              Arrays.stream(securityGroupRules.getEgress())
                                  .map(
                                      egress -> {
                                        TencentSecurityGroupRule outRule =
                                            TencentSecurityGroupRule.builder()
                                                .index(egress.getPolicyIndex())
                                                .protocol(egress.getProtocol())
                                                .port(egress.getPort())
                                                .cidrBlock(egress.getCidrBlock())
                                                .action(egress.getAction())
                                                .build();
                                        return outRule;
                                      })
                                  .collect(Collectors.toList()))
                          .lastReadTime(System.currentTimeMillis())
                          .build();
                  return securityGroupDesc;
                })
            .collect(Collectors.toSet());
    return securityGroupDescriptionSet;
  }

  private TencentSecurityGroupDescription loadSecurityGroupById(String securityGroupId) {
    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);

    SecurityGroup securityGroup = vpcClient.getSecurityGroupById(securityGroupId).get(0);
    long currentTime = System.currentTimeMillis();
    if (securityGroup != null) {
      TencentSecurityGroupDescription description =
          TencentSecurityGroupDescription.builder().build();
      TencentSecurityGroupDescription securityGroupDesc =
          description.setSecurityGroupId(securityGroup.getSecurityGroupId());
      description.setSecurityGroupDesc(securityGroup.getSecurityGroupDesc());
      description.setSecurityGroupName(securityGroup.getSecurityGroupName());
      description.setLastReadTime(currentTime);
      SecurityGroupPolicySet securityGroupRules =
          vpcClient.getSecurityGroupPolicies(securityGroupDesc.getSecurityGroupId());
      securityGroupDesc.setInRules(
          Arrays.stream(securityGroupRules.getIngress())
              .map(
                  ingress -> {
                    TencentSecurityGroupRule inRule =
                        TencentSecurityGroupRule.builder()
                            .index(ingress.getPolicyIndex())
                            .protocol(ingress.getProtocol())
                            .port(ingress.getPort())
                            .cidrBlock(ingress.getCidrBlock())
                            .action(ingress.getAction())
                            .build();
                    return inRule;
                  })
              .collect(Collectors.toList()));
      securityGroupDesc.setOutRules(
          Arrays.stream(securityGroupRules.getEgress())
              .map(
                  egress -> {
                    TencentSecurityGroupRule outRule =
                        TencentSecurityGroupRule.builder()
                            .index(egress.getPolicyIndex())
                            .protocol(egress.getProtocol())
                            .port(egress.getPort())
                            .cidrBlock(egress.getCidrBlock())
                            .action(egress.getAction())
                            .build();
                    return outRule;
                  })
              .collect(Collectors.toList()));
      return securityGroupDesc;
    }
    return null;
  }

  private CacheResult buildCacheResult(
      ProviderCache providerCache,
      Collection<TencentSecurityGroupDescription> securityGroups,
      final long lastReadTime,
      TencentSecurityGroupDescription updatedSecurityGroup,
      TencentSecurityGroupDescription evictedSecurityGroup) {
    if (!CollectionUtils.isEmpty(securityGroups)) {
      final List<CacheData> data = new ArrayList<CacheData>();
      Collection<String> identifiers =
          providerCache.filterIdentifiers(
              ON_DEMAND.ns, Keys.getSecurityGroupKey("*", "*", accountName, region));
      Collection<CacheData> onDemandCacheResults =
          providerCache.getAll(ON_DEMAND.ns, identifiers, RelationshipCacheFilter.none());

      // Add any outdated OnDemand cache entries to the evicted list
      final List<String> evictions = new ArrayList<String>();
      final Map<String, CacheData> usableOnDemandCacheDatas =
          new LinkedHashMap<String, CacheData>();
      onDemandCacheResults.forEach(
          it -> {
            if ((Long) it.getAttributes().get("cachedTime") < lastReadTime) {
              evictions.add(it.getId());
            } else {
              usableOnDemandCacheDatas.put(it.getId(), it);
            }
          });

      securityGroups.forEach(
          item -> {
            TencentSecurityGroupDescription securityGroup = item;

            String sgKey =
                Keys.getSecurityGroupKey(
                    securityGroup.getSecurityGroupId(),
                    securityGroup.getSecurityGroupName(),
                    getAccountName(),
                    getRegion());

            // Search the current OnDemand update map entries and look for a security group match
            CacheData onDemandSG = usableOnDemandCacheDatas.get(sgKey);
            if (onDemandSG != null) {
              if ((Long) onDemandSG.getAttributes().get("cachedTime")
                  > securityGroup.getLastReadTime()) {
                // Found a security group resource that has been updated since last time was read
                // from Azure cloud
                try {
                  securityGroup =
                      getObjectMapper()
                          .readValue(
                              (String) onDemandSG.getAttributes().get("securityGroup"),
                              TencentSecurityGroupDescription.class);
                } catch (IOException e) {
                  e.printStackTrace();
                  log.error("buildCacheResult error", e);
                }
              } else {
                // Found a Security Group that has been deleted since last time was read from
                // Tencent cloud
                securityGroup = null;
              }

              // There's no need to keep this entry in the map
              usableOnDemandCacheDatas.remove(sgKey);
            }

            if (securityGroup != null) {
              data.add(buildCacheData(securityGroup));
            }
          });

      log.info("Caching " + data.size() + " items in " + getAgentType());

      return new DefaultCacheResult(
          new HashMap<String, Collection<CacheData>>() {
            {
              put(SECURITY_GROUPS.ns, data);
            }
          },
          new HashMap<String, Collection<String>>() {
            {
              put(ON_DEMAND.ns, evictions);
            }
          });
    } else {
      if (updatedSecurityGroup != null) {
        // This is an OnDemand update/edit request for a given security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, updatedSecurityGroup, "OnDemandUpdated")) {
          CacheData data = buildCacheData(updatedSecurityGroup);
          log.info("Caching 1 OnDemand updated item in " + getAgentType());
          return new DefaultCacheResult(
              new HashMap<String, Collection<CacheData>>() {
                {
                  put(SECURITY_GROUPS.ns, new ArrayList<CacheData>(Arrays.asList(data)));
                }
              });
        } else {
          return null;
        }
      }

      if (evictedSecurityGroup != null) {
        // This is an OnDemand delete request for a given Azure network security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, evictedSecurityGroup, "OnDemandEvicted")) {
          log.info("Caching 1 OnDemand evicted item in " + getAgentType());
          return new DefaultCacheResult(
              new HashMap<String, Collection<CacheData>>() {
                {
                  put(SECURITY_GROUPS.ns, new ArrayList());
                }
              });
        } else {
          return null;
        }
      }
    }

    return new DefaultCacheResult(
        new HashMap<String, Collection<CacheData>>() {
          {
            put(SECURITY_GROUPS.ns, new ArrayList());
          }
        });
  }

  private CacheData buildCacheData(TencentSecurityGroupDescription securityGroup) {
    Map<String, Object> attributes = new HashMap<>(1);
    attributes.put(SECURITY_GROUPS.ns, securityGroup);

    return new DefaultCacheData(
        Keys.getSecurityGroupKey(
            securityGroup.getSecurityGroupId(),
            securityGroup.getSecurityGroupName(),
            accountName,
            region),
        attributes,
        new HashMap<String, Collection<String>>());
  }

  private Boolean updateCache(
      ProviderCache providerCache,
      final TencentSecurityGroupDescription securityGroup,
      String onDemandCacheType) {
    Boolean foundUpdatedOnDemandSG = false;

    if (securityGroup != null) {
      // Get the current list of all OnDemand requests from the cache
      Collection<CacheData> cacheResults =
          providerCache.getAll(
              ON_DEMAND.ns,
              new ArrayList<String>(
                  Arrays.asList(
                      Keys.getSecurityGroupKey(
                          securityGroup.getSecurityGroupId(),
                          securityGroup.getSecurityGroupName(),
                          accountName,
                          region))));

      if (!CollectionUtils.isEmpty(cacheResults)) {
        for (CacheData it : cacheResults) {
          // cacheResults.each should only return one item which is matching the given security
          // group details
          if ((Long) it.getAttributes().get("cachedTime") > securityGroup.getLastReadTime()) {
            // Found a newer matching entry in the cache when compared with the current OnDemand
            // request
            foundUpdatedOnDemandSG = true;
          }
        }
      }

      if (!foundUpdatedOnDemandSG) {
        // Add entry to the OnDemand respective cache
        Map<String, Object> map = new HashMap<>(3);
        try {
          map.put("securityGroup", objectMapper.writeValueAsString(securityGroup));
        } catch (JsonProcessingException e) {
          e.printStackTrace();
          log.error("updateCache error", e);
        }
        map.put("cachedTime", securityGroup.getLastReadTime());
        map.put("onDemandCacheType", onDemandCacheType);
        DefaultCacheData cacheData =
            new DefaultCacheData(
                Keys.getSecurityGroupKey(
                    securityGroup.getSecurityGroupId(),
                    securityGroup.getSecurityGroupName(),
                    accountName,
                    region),
                map,
                new HashMap<>());
        providerCache.putCacheData(ON_DEMAND.ns, cacheData);
        return true;
      }
    }
    return false;
  }

  public final OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  public String getOnDemandAgentType() {
    return onDemandAgentType;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final ObjectMapper objectMapper;
  private final String region;
  private final String accountName;
  private final TencentNamedAccountCredentials credentials;
  private final String providerName = TencentInfrastructureProvider.class.getName();
  private final Registry registry;
  private final OnDemandMetricsSupport metricsSupport;
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(SECURITY_GROUPS.ns));
        }
      };
}
