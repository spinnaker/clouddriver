package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.model.*;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.as.v20180419.models.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class TencentServerGroupCachingAgent extends AbstractTencentCachingAgent
    implements OnDemandAgent {
  public TencentServerGroupCachingAgent(
      TencentNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(credentials, objectMapper, region);
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            TencentCloudProvider.ID + ":" + String.valueOf(OnDemandType.ServerGroup));
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(TencentBasicResource.class);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    final Long start = System.currentTimeMillis();
    log.info("start load data");
    AutoScalingClient client =
        new AutoScalingClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());

    List<TencentServerGroup> serverGroups = loadAsgAsServerGroup(client);
    final List<CacheData> toEvictOnDemandCacheData = new ArrayList<>();
    final List<CacheData> toKeepOnDemandCacheData = new ArrayList<>();

    final Set<String> serverGroupKeys =
        serverGroups.stream()
            .map(
                it -> {
                  return Keys.getServerGroupKey(
                      it.getName(), getCredentials().getName(), getRegion());
                })
            .collect(Collectors.toSet());

    List<String> pendingOnDemandRequestKeys =
        providerCache
            .filterIdentifiers(
                ON_DEMAND.ns,
                Keys.getServerGroupKey("*", "*", getCredentials().getName(), getRegion()))
            .stream()
            .filter(it -> serverGroupKeys.contains(it))
            .collect(Collectors.toList());

    Collection<CacheData> pendingOnDemandRequestsForServerGroups =
        providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys);
    pendingOnDemandRequestsForServerGroups.forEach(
        it -> {
          if ((Long) it.getAttributes().get("cacheTime") < start
              && (Integer) it.getAttributes().get("processedCount") > 0) {
            toEvictOnDemandCacheData.add(it);
          } else {
            toKeepOnDemandCacheData.add(it);
          }
        });

    CacheResult result =
        buildCacheResult(serverGroups, toKeepOnDemandCacheData, toEvictOnDemandCacheData);

    result
        .getCacheResults()
        .get(ON_DEMAND.ns)
        .forEach(
            it -> {
              it.getAttributes().put("processedTime", System.currentTimeMillis());
              it.getAttributes().putIfAbsent("processedCount", -1);
              final Integer count = (Integer) it.getAttributes().get("processedCount");
              it.getAttributes().put("processedCount", count + 1);
            });
    return result;
  }

  private CacheResult buildCacheResult(
      Collection<TencentServerGroup> serverGroups,
      final Collection<CacheData> toKeepOnDemandCacheData,
      Collection<CacheData> toEvictOnDemandCacheData) {
    log.info("Start build cache for " + getAgentType());

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    Map<String, Collection<String>> evictions =
        new HashMap<String, Collection<String>>() {
          {
            put(
                ON_DEMAND.ns,
                toEvictOnDemandCacheData.stream()
                    .map(it -> it.getId())
                    .collect(Collectors.toList()));
          }
        };

    final NamespaceCache namespaceCache = new NamespaceCache();

    serverGroups.forEach(
        it -> {
          Moniker moniker = getNamer().deriveMoniker(it);
          String applicationName = moniker.getApp();
          String clusterName = moniker.getCluster();

          if (applicationName == null || clusterName == null) {
            return;
          }

          final String serverGroupKey =
              Keys.getServerGroupKey(it.getName(), getAccountName(), getRegion());
          String clusterKey = Keys.getClusterKey(clusterName, applicationName, getAccountName());
          String appKey = Keys.getApplicationKey(applicationName);
          final List<String> instanceKeys = new ArrayList<String>();
          final List<String> loadBalancerKeys = new ArrayList<String>();

          Set<TencentInstance> instances = it.getInstances();
          instances.forEach(
              instance -> {
                instanceKeys.add(
                    Keys.getInstanceKey(instance.getName(), getAccountName(), getRegion()));
              });

          Set<String> loadBalancerIds = it.getLoadBalancers();
          loadBalancerIds.forEach(
              lbId -> {
                loadBalancerKeys.add(Keys.getLoadBalancerKey(lbId, getAccountName(), getRegion()));
              });

          // application
          Map<String, CacheData> applications = namespaceCache.get(APPLICATIONS.ns);
          applications.get(appKey).getAttributes().put("name", applicationName);
          applications.get(appKey).getRelationships().get(CLUSTERS.ns).add(clusterKey);
          applications.get(appKey).getRelationships().get(SERVER_GROUPS.ns).add(serverGroupKey);

          // cluster
          namespaceCache.get(CLUSTERS.ns).get(clusterKey).getAttributes().put("name", clusterName);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getAttributes()
              .put("accountName", getAccountName());
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(APPLICATIONS.ns)
              .add(appKey);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(SERVER_GROUPS.ns)
              .add(serverGroupKey);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(INSTANCES.ns)
              .addAll(instanceKeys);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(LOAD_BALANCERS.ns)
              .addAll(loadBalancerKeys);

          // loadBalancer
          loadBalancerKeys.forEach(
              lbKey -> {
                namespaceCache
                    .get(LOAD_BALANCERS.ns)
                    .get(lbKey)
                    .getRelationships()
                    .get(SERVER_GROUPS.ns)
                    .add(serverGroupKey);
              });

          // server group
          CacheData onDemandServerGroupCache =
              toKeepOnDemandCacheData.stream()
                  .filter(c -> c.getAttributes().get("name").equals(serverGroupKey))
                  .findFirst()
                  .orElse(null);

          // log.info("TencentServerGroupCachingAgent buildCacheResult serverGroup is {}", it);
          if (onDemandServerGroupCache != null) {
            mergeOnDemandCache(onDemandServerGroupCache, namespaceCache);
          } else {
            Map<String, Object> attributes =
                namespaceCache.get(SERVER_GROUPS.ns).get(serverGroupKey).getAttributes();
            Map<String, Collection<String>> relations =
                namespaceCache.get(SERVER_GROUPS.ns).get(serverGroupKey).getRelationships();
            attributes.put("asg", it.getAsg());
            attributes.put("accountName", getAccountName());
            attributes.put("name", it.getName());
            attributes.put("region", it.getRegion());
            attributes.put("launchConfig", it.getLaunchConfig());
            attributes.put("disabled", it.getDisabled());
            attributes.put("scalingPolicies", it.getScalingPolicies());
            attributes.put("scheduledActions", it.getScheduledActions());
            relations.get(APPLICATIONS.ns).add(appKey);
            relations.get(CLUSTERS.ns).add(clusterKey);
            relations.get(INSTANCES.ns).addAll(instanceKeys);
            relations.get(LOAD_BALANCERS.ns).addAll(loadBalancerKeys);
          }
        });

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> {
          cacheResults.put(namespace, cacheDataMap.values());
        });

    cacheResults.put(ON_DEMAND.ns, toKeepOnDemandCacheData);

    CacheResult result = new DefaultCacheResult(cacheResults, evictions);

    return result;
  }

  public Map<String, List<MutableCacheData>> mergeOnDemandCache(
      CacheData onDemandServerGroupCache,
      final Map<String, ? extends Map<String, CacheData>> namespaceCache) {

    Map<String, List<MutableCacheData>> onDemandCache = null;
    try {
      onDemandCache =
          getObjectMapper()
              .readValue(
                  (String) onDemandServerGroupCache.getAttributes().get("cacheResults"),
                  new TypeReference<Map<String, List<MutableCacheData>>>() {});
    } catch (IOException e) {
      log.error("mergeOnDemandCache error", e);
      e.printStackTrace();
      return null;
    }

    onDemandCache.forEach(
        (namespace, cacheDataList) -> {
          if (!namespace.equals("onDemand")) {
            cacheDataList.forEach(
                it -> {
                  final CacheData existingCacheData = namespaceCache.get(namespace).get(it.getId());
                  if (existingCacheData != null) {
                    namespaceCache.get(namespace).put(it.getId(), it);
                  } else {
                    existingCacheData.getAttributes().putAll(it.getAttributes());
                    it.getRelationships()
                        .forEach(
                            (relationshipName, relationships) -> {
                              existingCacheData
                                  .getRelationships()
                                  .get(relationshipName)
                                  .addAll(relationships);
                            });
                  }
                });
          }
        });
    return null;
  }

  public List<TencentServerGroup> loadAsgAsServerGroup(
      AutoScalingClient client, String serverGroupName) {
    List<AutoScalingGroup> asgs;
    if (!StringUtils.isEmpty(serverGroupName)) {
      asgs = client.getAutoScalingGroupsByName(serverGroupName);
    } else {
      asgs = client.getAllAutoScalingGroups();
    }

    List<String> launchConfigurationIds =
        asgs.stream()
            .map(
                it -> {
                  return it.getLaunchConfigurationId();
                })
            .collect(Collectors.toList());

    List<LaunchConfiguration> launchConfigurations =
        client.getLaunchConfigurations(launchConfigurationIds);

    List<Map> scalingPolicies = loadScalingPolicies(client);

    List<Map> scheduledActions = loadScheduledActions(client);

    List<Map> autoScalingInstances = loadAutoScalingInstances(client);

    return asgs.stream()
        .map(
            it -> {
              String autoScalingGroupId = it.getAutoScalingGroupId();
              String autoScalingGroupName = it.getAutoScalingGroupName();
              boolean disabled = it.getEnabledStatus().equals("DISABLED");
              TencentServerGroup serverGroup =
                  TencentServerGroup.builder()
                      .accountName(getAccountName())
                      .region(getRegion())
                      .name(autoScalingGroupName)
                      .disabled(disabled)
                      .build();

              Map<String, Object> asg = getObjectMapper().convertValue(it, getATTRIBUTES());
              serverGroup.setAsg(asg);

              String launchConfigurationId = it.getLaunchConfigurationId();
              LaunchConfiguration launchConfiguration =
                  launchConfigurations.stream()
                      .filter(
                          conf -> {
                            return conf.getLaunchConfigurationId().equals(launchConfigurationId);
                          })
                      .findFirst()
                      .orElse(null);
              Map<String, Object> asc =
                  getObjectMapper().convertValue(launchConfiguration, getATTRIBUTES());
              serverGroup.setLaunchConfig(asc);

              serverGroup.setScalingPolicies(
                  scalingPolicies.stream()
                      .filter(
                          policy -> {
                            return policy.get("autoScalingGroupId").equals(autoScalingGroupId);
                          })
                      .collect(Collectors.toList()));

              serverGroup.setScheduledActions(
                  scheduledActions.stream()
                      .filter(
                          action -> {
                            return action.get("autoScalingGroupId").equals(autoScalingGroupId);
                          })
                      .collect(Collectors.toList()));

              List<Map> instances =
                  autoScalingInstances.stream()
                      .filter(
                          instance -> {
                            return instance.get("autoScalingGroupId").equals(autoScalingGroupId);
                          })
                      .collect(Collectors.toList());

              instances.forEach(
                  i -> {
                    String health = (String) i.get("healthStatus");
                    TencentInstanceHealth.Status healthStatus = null;
                    TencentInstanceHealth instanceHealth = new TencentInstanceHealth();
                    if (health.equals("HEALTHY")) {
                      healthStatus = TencentInstanceHealth.Status.RUNNING;
                      instanceHealth.setInstanceStatus(healthStatus);
                    }
                    TencentInstance instance =
                        TencentInstance.builder()
                            .name((String) i.get("instanceId"))
                            .instanceHealth(instanceHealth)
                            .serverGroupName(serverGroup.getName())
                            .launchTime(
                                AutoScalingClient.ConvertIsoDateTime((String) i.get("addTime"))
                                    .getTime())
                            .zone((String) i.get("zone"))
                            .build();
                    // log.info("TencentServerGroupCachingAgent loadAsgServerGroup iter through
                    // instance {}", instance);
                    serverGroup.getInstances().add(instance);
                  });
              return serverGroup;
            })
        .collect(Collectors.toList());
  }

  public List<TencentServerGroup> loadAsgAsServerGroup(AutoScalingClient client) {
    return loadAsgAsServerGroup(client, null);
  }

  private List<Map> loadScalingPolicies(AutoScalingClient client, String autoScalingGroupId) {
    List<ScalingPolicy> scalingPolicies = client.getScalingPolicies(autoScalingGroupId);
    return scalingPolicies.stream()
        .map(
            it -> {
              Map<String, Object> asp = getObjectMapper().convertValue(it, getATTRIBUTES());
              return asp;
            })
        .collect(Collectors.toList());
  }

  private List<Map> loadScalingPolicies(AutoScalingClient client) {
    return loadScalingPolicies(client, null);
  }

  private List<Map> loadScheduledActions(AutoScalingClient client, String autoScalingGroupId) {
    List<ScheduledAction> scheduledActions = client.getScheduledAction(autoScalingGroupId);
    return scheduledActions.stream()
        .map(
            it -> {
              Map<String, Object> asst = getObjectMapper().convertValue(it, getATTRIBUTES());
              return asst;
            })
        .collect(Collectors.toList());
  }

  private List<Map> loadScheduledActions(AutoScalingClient client) {
    return loadScheduledActions(client, null);
  }

  private List<Map> loadAutoScalingInstances(AutoScalingClient client, String autoScalingGroupId) {
    List<Instance> autoScalingInstances = client.getAutoScalingInstances(autoScalingGroupId);
    return autoScalingInstances.stream()
        .map(
            it -> {
              Map<String, Object> asi = getObjectMapper().convertValue(it, getATTRIBUTES());
              return asi;
            })
        .collect(Collectors.toList());
  }

  private List<Map> loadAutoScalingInstances(AutoScalingClient client) {
    return loadAutoScalingInstances(client, null);
  }

  @Override
  public OnDemandResult handle(
      final ProviderCache providerCache, final Map<String, ? extends Object> data) {
    log.info("TencentServerGroupCachingAgent handle, data is {}", data);
    if (!data.containsKey("serverGroupName")
        || !data.containsKey("accountName")
        || !data.containsKey("region")
        || !data.get("accountName").equals(getAccountName())
        || !data.get("region").equals(getRegion())) {
      return null;
    }

    log.info("Enter tencent server group agent handle " + data.get("serverGroupName"));
    final AutoScalingClient client =
        new AutoScalingClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());
    final TencentServerGroup serverGroup =
        metricsSupport.readData(
            () -> {
              return loadAsgAsServerGroup(client, (String) data.get("serverGroupName")).get(0);
            });

    if (serverGroup == null) {
      return null;
    }

    log.info("TencentServerGroupCachingAgent, serverGroup is {}", serverGroup);
    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> {
              return buildCacheResult(
                  new ArrayList<TencentServerGroup>(Arrays.asList(serverGroup)), null, null);
            });

    try {
      final String cacheResultAsJson =
          getObjectMapper().writeValueAsString(cacheResult.getCacheResults());

      final Moniker moniker = (serverGroup == null ? null : serverGroup.getMoniker());
      final String serverGroupKey =
          Keys.getServerGroupKey(
              (moniker == null ? null : moniker.getCluster()),
              (String) data.get("serverGroupName"),
              getAccountName(),
              getRegion());

      if (cacheResult.getCacheResults().values().stream().allMatch(it -> it == null)) {
        // Avoid writing an empty onDemand cache record (instead delete any that may have previously
        // existed).
        providerCache.evictDeletedItems(
            ON_DEMAND.ns, new ArrayList<String>(Arrays.asList(serverGroupKey)));
      } else {
        metricsSupport.onDemandStore(
            () -> {
              Map<String, Object> map = new HashMap<>(2);
              map.put("cacheTime", new Date());
              map.put("cacheResults", cacheResultAsJson);
              DefaultCacheData cacheData =
                  new DefaultCacheData(serverGroupKey, 10 * 60, map, new HashMap<>());
              providerCache.putCacheData(ON_DEMAND.ns, cacheData);
              return null;
            });
      }

      Map<String, Collection<String>> evictions =
          !CollectionUtils.isEmpty(serverGroup.getAsg())
              ? new HashMap<>()
              : new HashMap<String, Collection<String>>() {
                {
                  put(
                      SERVER_GROUPS.ns,
                      new ArrayList<String>() {
                        {
                          add(serverGroupKey);
                        }
                      });
                }
              };

      OnDemandResult result = new OnDemandResult();
      result.setSourceAgentType(getOnDemandAgentType());
      result.setCacheResult(cacheResult);
      result.setEvictions(evictions);
      return result;
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      log.error("handle error", e);
    }
    return null;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(
            ON_DEMAND.ns,
            Keys.getServerGroupKey("*", "*", getCredentials().getName(), getRegion()));
    return fetchPendingOnDemandRequests(providerCache, keys);
  }

  private Collection<Map> fetchPendingOnDemandRequests(
      ProviderCache providerCache, Collection<String> keys) {
    return providerCache.getAll(ON_DEMAND.ns, keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              Map<String, String> details = Keys.parse(it.getId());
              Map<String, Object> map = new HashMap<String, Object>(7);
              map.put("id", it.getId());
              map.put("details", details);
              map.put("moniker", convertOnDemandDetails(details));
              map.put("cacheTime", it.getAttributes().get("cacheTime"));
              map.put("cacheExpiry", it.getAttributes().get("cacheExpiry"));
              map.put("processedCount", it.getAttributes().get("processedCount"));
              map.put("processedTime", it.getAttributes().get("processedTime"));
              return map;
            })
        .collect(Collectors.toList());
  }

  public String getOnDemandAgentType() {
    return onDemandAgentType;
  }

  public final OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final OnDemandMetricsSupport metricsSupport;
  private final Namer<TencentBasicResource> namer;
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(APPLICATIONS.ns));
          add(AUTHORITATIVE.forType(SERVER_GROUPS.ns));
          add(INFORMATIVE.forType(CLUSTERS.ns));
          add(INFORMATIVE.forType(INSTANCES.ns));
          add(INFORMATIVE.forType(LOAD_BALANCERS.ns));
        }
      };
}
