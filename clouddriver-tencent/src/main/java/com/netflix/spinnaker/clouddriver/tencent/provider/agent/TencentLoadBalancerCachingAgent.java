package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencent.model.NamespaceCache;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.*;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.clb.v20180317.models.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Data
public class TencentLoadBalancerCachingAgent implements OnDemandAgent, CachingAgent, AccountAware {
  public TencentLoadBalancerCachingAgent(
      TencentNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    this.credentials = credentials;
    this.accountName = credentials.getName();
    this.region = region;
    this.objectMapper = objectMapper;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, TencentCloudProvider.ID + ":" + OnDemandType.LoadBalancer);
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(TencentBasicResource.class);
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

  public List<TencentLoadBalancer> loadLoadBalancerData(String loadBalancerId) {
    LoadBalancerClient client =
        new LoadBalancerClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);

    List<LoadBalancer> lbSet = new ArrayList<>();
    if (!StringUtils.isEmpty(loadBalancerId)) {
      lbSet = client.getLoadBalancerById(loadBalancerId);
    } else {
      lbSet = client.getAllLoadBalancer();
    }

    List<TencentLoadBalancer> loadBanancerList =
        lbSet.stream()
            .map(
                it -> {
                  TencentLoadBalancer loadBalancer =
                      TencentLoadBalancer.builder()
                          .region(getRegion())
                          .accountName(getAccountName())
                          .name(it.getLoadBalancerName())
                          .loadBalancerName(it.getLoadBalancerName())
                          .id(it.getLoadBalancerId())
                          .loadBalancerId(it.getLoadBalancerId())
                          .loadBalancerType(it.getLoadBalancerType())
                          .vpcId(it.getVpcId())
                          .subnetId(it.getSubnetId())
                          .createTime(it.getCreateTime())
                          .loadBalacnerVips(Arrays.asList(it.getLoadBalancerVips().clone()))
                          .securityGroups(Arrays.asList(it.getSecureGroups().clone()))
                          .build();

                  List<Listener> queryListeners = client.getAllLBListener(loadBalancer.getId());
                  List<String> listenerIdList =
                      queryListeners.stream()
                          .map(
                              listener -> {
                                return listener.getListenerId();
                              })
                          .collect(Collectors.toList());
                  // all listener's targets
                  final List<ListenerBackend> lbTargetList = new ArrayList<>();
                  if (listenerIdList.size() > 0) {
                    lbTargetList.addAll(
                        client.getLBTargetList(loadBalancer.getId(), listenerIdList));
                  }

                  List<TencentLoadBalancerListener> listeners =
                      queryListeners.stream()
                          .map(
                              li -> {
                                TencentLoadBalancerListener.TencentLoadBalancerListenerBuilder
                                    builder =
                                        TencentLoadBalancerListener.builder()
                                            .listenerId(li.getListenerId())
                                            .protocol(li.getProtocol())
                                            .port(li.getPort())
                                            .scheduler(li.getScheduler())
                                            .sessionExpireTime(li.getSessionExpireTime())
                                            .sniSwitch(li.getSniSwitch())
                                            .listenerName(li.getListenerName());
                                if (li.getCertificate() != null) { // listener.certificate
                                  builder.certificate(
                                      TencentLoadBalancerCertificate.builder()
                                          .sslMode(li.getCertificate().getSSLMode())
                                          .certId(li.getCertificate().getCertId())
                                          .certCaId(li.getCertificate().getCertCaId())
                                          .build());
                                }

                                if (li.getHealthCheck() != null) { // listener healtch check
                                  builder.healthCheck(
                                      TencentLoadBalancerHealthCheck.builder()
                                          .healthSwitch(li.getHealthCheck().getHealthSwitch())
                                          .timeOut(li.getHealthCheck().getTimeOut())
                                          .intervalTime(li.getHealthCheck().getIntervalTime())
                                          .healthNum(li.getHealthCheck().getHealthNum())
                                          .unHealthNum(li.getHealthCheck().getUnHealthNum())
                                          .httpCode(li.getHealthCheck().getHttpCode())
                                          .httpCheckPath(li.getHealthCheck().getHttpCheckPath())
                                          .httpCheckDomain(li.getHealthCheck().getHttpCheckDomain())
                                          .httpCheckMethod(li.getHealthCheck().getHttpCheckMethod())
                                          .build());
                                }

                                // targets 4 layer
                                // def lbTargets = client.getLBTargets(loadBalancer.loadBalancerId,
                                // listener.listenerId)
                                List<ListenerBackend> lbTargets =
                                    lbTargetList.stream()
                                        .filter(
                                            backend -> {
                                              return li.getListenerId()
                                                  .equals(backend.getListenerId());
                                            })
                                        .collect(Collectors.toList());
                                lbTargets.stream()
                                    .forEach(
                                        listenerBackend -> {
                                          if (!ArrayUtils.isEmpty(listenerBackend.getTargets())) {
                                            builder.targets(
                                                Arrays.stream(listenerBackend.getTargets())
                                                    .map(
                                                        targetEntry -> {
                                                          if (targetEntry != null) {
                                                            TencentLoadBalancerTarget target =
                                                                TencentLoadBalancerTarget.builder()
                                                                    .instanceId(
                                                                        targetEntry.getInstanceId())
                                                                    .port(targetEntry.getPort())
                                                                    .weight(targetEntry.getWeight())
                                                                    .type(targetEntry.getType())
                                                                    .build();
                                                            return target;
                                                          } else {
                                                            return null;
                                                          }
                                                        })
                                                    .collect(Collectors.toList()));
                                          }
                                        });

                                // rules
                                List<TencentLoadBalancerRule> rules =
                                    Arrays.stream(
                                            Optional.ofNullable(li.getRules())
                                                .orElse(new RuleOutput[] {}))
                                        .map(
                                            r -> {
                                              TencentLoadBalancerRule rule =
                                                  new TencentLoadBalancerRule();
                                              rule.setLocationId(r.getLocationId());
                                              rule.setDomain(r.getDomain());
                                              rule.setUrl(r.getUrl());
                                              if (r.getCertificate() != null) { // rule.certificate
                                                rule.setCertificate(
                                                    TencentLoadBalancerCertificate.builder()
                                                        .sslMode(r.getCertificate().getSSLMode())
                                                        .certId(r.getCertificate().getCertId())
                                                        .certCaId(r.getCertificate().getCertCaId())
                                                        .build());
                                              }

                                              if (r.getHealthCheck() != null) { // rule healthCheck
                                                rule.setHealthCheck(
                                                    TencentLoadBalancerHealthCheck.builder()
                                                        .healthSwitch(
                                                            r.getHealthCheck().getHealthSwitch())
                                                        .timeOut(r.getHealthCheck().getTimeOut())
                                                        .intervalTime(
                                                            r.getHealthCheck().getIntervalTime())
                                                        .healthNum(
                                                            r.getHealthCheck().getHealthNum())
                                                        .unHealthNum(
                                                            r.getHealthCheck().getUnHealthNum())
                                                        .httpCode(r.getHealthCheck().getHttpCode())
                                                        .httpCheckPath(
                                                            r.getHealthCheck().getHttpCheckPath())
                                                        .httpCheckDomain(
                                                            r.getHealthCheck().getHttpCheckDomain())
                                                        .httpCheckMethod(
                                                            r.getHealthCheck().getHttpCheckMethod())
                                                        .build());
                                              }

                                              // rule targets 7Larer
                                              lbTargets.stream()
                                                  .forEach(
                                                      listenBackend -> {
                                                        if (!ArrayUtils.isEmpty(
                                                            listenBackend.getRules())) {
                                                          for (RuleTargets ruleTarget :
                                                              listenBackend.getRules()) {
                                                            if (ruleTarget
                                                                    .getLocationId()
                                                                    .equals(rule.getLocationId())
                                                                && !ArrayUtils.isEmpty(
                                                                    ruleTarget.getTargets())) {
                                                              rule.setTargets(
                                                                  Arrays.stream(
                                                                          ruleTarget.getTargets())
                                                                      .map(
                                                                          ruleTargetEntry -> {
                                                                            TencentLoadBalancerTarget
                                                                                target =
                                                                                    TencentLoadBalancerTarget
                                                                                        .builder()
                                                                                        .instanceId(
                                                                                            ruleTargetEntry
                                                                                                .getInstanceId())
                                                                                        .port(
                                                                                            ruleTargetEntry
                                                                                                .getPort())
                                                                                        .weight(
                                                                                            ruleTargetEntry
                                                                                                .getWeight())
                                                                                        .type(
                                                                                            ruleTargetEntry
                                                                                                .getType())
                                                                                        .build();
                                                                            return target;
                                                                          })
                                                                      .collect(
                                                                          Collectors.toList()));
                                                            }
                                                          }
                                                        }
                                                      });
                                              return rule;
                                            })
                                        .collect(Collectors.toList());

                                builder.rules(rules);
                                return builder.build(); // build a loadbalancerlistener
                              })
                          .collect(Collectors.toList());
                  loadBalancer.setListeners(listeners);
                  return loadBalancer;
                })
            .collect(Collectors.toList());
    return loadBanancerList;
  }

  public List<TencentLoadBalancer> loadLoadBalancerData() {
    return loadLoadBalancerData(null);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.LoadBalancer) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(
      final ProviderCache providerCache, final Map<String, ? extends Object> data) {
    log.info("Enter handle, data = " + String.valueOf(data));
    if (!data.containsKey("loadBalancerId")
        || !data.containsKey("account")
        || !data.containsKey("region")
        || !accountName.equals(data.get("account"))
        || !region.equals(data.get("region"))) {
      return null;
    }

    final TencentLoadBalancer loadBalancer =
        metricsSupport.readData(
            () -> {
              return loadLoadBalancerData((String) data.get("loadBalancerId")).get(0);
            });
    if (loadBalancer == null) {
      log.info("Can not find loadBalancer " + data.get("loadBalancerId"));
      return null;
    }

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> {
              return buildCacheResult(
                  new ArrayList<TencentLoadBalancer>(Arrays.asList(loadBalancer)), null, null);
            });

    final String cacheResultAsJson;
    try {
      cacheResultAsJson = objectMapper.writeValueAsString(cacheResult.getCacheResults());
    } catch (JsonProcessingException e) {
      log.error("tencentLoadBalancerCachingAgent handle error", e);
      e.printStackTrace();
      return null;
    }
    final String loadBalancerKey =
        Keys.getLoadBalancerKey((String) data.get("loadBalancerId"), accountName, region);
    if (cacheResult.getCacheResults().values().stream().allMatch(it -> it == null)) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously
      // existed).
      providerCache.evictDeletedItems(
          ON_DEMAND.ns, new ArrayList<String>(Arrays.asList(loadBalancerKey)));
    } else {
      metricsSupport.onDemandStore(
          () -> {
            Map<String, Object> map = new HashMap<>(2);
            map.put("cacheTime", new Date());
            map.put("cacheResults", cacheResultAsJson);
            DefaultCacheData cacheData =
                new DefaultCacheData(loadBalancerKey, 10 * 60, map, new HashMap<>());
            providerCache.putCacheData(ON_DEMAND.ns, cacheData);
            return null;
          });
    }

    Map<String, Collection<String>> evictions = new HashMap<>();
    if (loadBalancer != null) {
      evictions.put(
          LOAD_BALANCERS.ns,
          new ArrayList<String>() {
            {
              add(loadBalancerKey);
            }
          });
    }

    OnDemandResult result = new OnDemandResult();

    result.setSourceAgentType(getOnDemandAgentType());
    result.setCacheResult(cacheResult);
    result.setEvictions(evictions);
    return result;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter LoadBalancerCacheingAgent loadData ");

    final List<TencentLoadBalancer> loadBalancerSet = loadLoadBalancerData();
    log.info("Total loadBanancre Number = " + loadBalancerSet.size() + " in " + getAgentType());
    final List<CacheData> toEvictOnDemandCacheData = new ArrayList();
    final List<CacheData> toKeepOnDemandCacheData = new ArrayList();

    final Long start = System.currentTimeMillis();
    final Set<String> loadBalancerKeys =
        loadBalancerSet.stream()
            .map(
                it -> {
                  return Keys.getLoadBalancerKey(it.getId(), credentials.getName(), region);
                })
            .collect(Collectors.toSet());

    List<String> pendingOnDemandRequestKeys =
        providerCache
            .filterIdentifiers(
                ON_DEMAND.ns, Keys.getLoadBalancerKey("*", credentials.getName(), region))
            .stream()
            .filter(it -> loadBalancerKeys.contains(it))
            .collect(Collectors.toList());

    Collection<CacheData> pendingOnDemandRequestsForloadBalancer =
        providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys);
    pendingOnDemandRequestsForloadBalancer.stream()
        .forEach(
            it -> {
              if ((Long) it.getAttributes().get("cacheTime") < start
                  && (Integer) it.getAttributes().get("processedCount") > 0) {
                toEvictOnDemandCacheData.add(it);
              } else {
                toKeepOnDemandCacheData.add(it);
              }
            });

    CacheResult result =
        buildCacheResult(loadBalancerSet, toKeepOnDemandCacheData, toEvictOnDemandCacheData);

    result.getCacheResults().get(ON_DEMAND.ns).stream()
        .forEach(
            it -> {
              it.getAttributes().put("processedTime", System.currentTimeMillis());
              final Integer processedCount = (Integer) it.getAttributes().get("processedCount");
              it.getAttributes().putIfAbsent("processedCount", -1);
              it.getAttributes()
                  .put("processedCount", (Integer) it.getAttributes().get("processedCount") + 1);
            });

    /*
    result.cacheResults.each { String namespace, Collection<CacheData> caches->
      log.info "namespace $namespace"
      caches.each{
        log.info "attributes: $it.attributes, relationships: $it.relationships"
      }
    }*/
    return result;
  }

  private CacheResult buildCacheResult(
      Collection<TencentLoadBalancer> loadBalancerSet,
      Collection<CacheData> toKeepOnDemandCacheData,
      Collection<CacheData> toEvictOnDemandCacheData) {
    log.info("Start build cache for " + getAgentType());

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    Map<String, Collection<String>> evictions = new HashMap<>();
    if (!CollectionUtils.isEmpty(toEvictOnDemandCacheData)) {
      evictions.put(
          ON_DEMAND.ns,
          toEvictOnDemandCacheData.stream().map(it -> it.getId()).collect(Collectors.toList()));
    }

    NamespaceCache namespaceCache = new NamespaceCache();

    loadBalancerSet.stream()
        .forEach(
            it -> {
              Moniker moniker = getNamer().deriveMoniker(it);
              String applicationName = moniker.getApp();
              if (applicationName == null) {
                return; // =continue
              }

              final String loadBalancerKey =
                  Keys.getLoadBalancerKey(it.getId(), getAccountName(), getRegion());
              String appKey = Keys.getApplicationKey(applicationName);
              // List<String> instanceKeys = []

              // application
              Map<String, CacheData> applications = namespaceCache.get(APPLICATIONS.ns);
              applications.get(appKey).getAttributes().put("name", applicationName);
              applications
                  .get(appKey)
                  .getRelationships()
                  .get(LOAD_BALANCERS.ns)
                  .add(loadBalancerKey);

              // compare onDemand
              // def onDemandLoadBalancerCache = toKeepOnDemandCacheData.find {
              //  it.id == loadBalancerKey
              // }
              boolean onDemandLoadBalancerCache = false;
              if (onDemandLoadBalancerCache) {
                // mergeOnDemandCache(onDemandLoadBalancerCache, namespaceCache)
              } else {
                // LoadBalancer
                Map<String, Object> attributes =
                    namespaceCache.get(LOAD_BALANCERS.ns).get(loadBalancerKey).getAttributes();
                attributes.put("application", applicationName);
                attributes.put("name", it.getName());
                attributes.put("region", it.getRegion());
                attributes.put("id", it.getId());
                attributes.put("loadBalancerId", it.getLoadBalancerId());
                attributes.put("accountName", getAccountName());
                attributes.put("vpcId", it.getVpcId());
                attributes.put("subnetId", it.getSubnetId());
                attributes.put("loadBalancerType", it.getLoadBalancerType());
                attributes.put("createTime", it.getCreateTime());
                attributes.put("loadBalacnerVips", new ArrayList<String>());
                it.getLoadBalacnerVips()
                    .forEach(vip -> ((List<String>) attributes.get("loadBalacnerVips")).add(vip));
                attributes.put("securityGroups", new ArrayList<String>());
                it.getSecurityGroups()
                    .forEach(sg -> ((List<String>) attributes.get("securityGroups")).add(sg));
                attributes.put("listeners", new ArrayList<TencentLoadBalancerListener>());
                it.getListeners()
                    .forEach(
                        li -> {
                          TencentLoadBalancerListener listener =
                              TencentLoadBalancerListener.builder().build();
                          listener.copyListener(li);
                          ((List<TencentLoadBalancerListener>) attributes.get("listeners"))
                              .add(listener);
                        });
                namespaceCache
                    .get(LOAD_BALANCERS.ns)
                    .get(loadBalancerKey)
                    .getRelationships()
                    .get(APPLICATIONS.ns)
                    .add(appKey);
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
      CacheData onDemandLoadBalancerCache,
      final Map<String, Map<String, CacheData>> namespaceCache) {
    Map<String, List<MutableCacheData>> onDemandCache = null;
    try {
      onDemandCache =
          objectMapper.readValue(
              (String) onDemandLoadBalancerCache.getAttributes().get("cacheResults"),
              new TypeReference<Map<String, List<MutableCacheData>>>() {});
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      log.error("mergeOnDemandCache error", e);
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      log.error("mergeOnDemandCache", e);
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
    return onDemandCache;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new ArrayList<Map>();
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

  private final String accountName;
  private final String region;
  private final ObjectMapper objectMapper;
  private final String providerName = TencentInfrastructureProvider.class.getName();
  private TencentNamedAccountCredentials credentials;
  private final OnDemandMetricsSupport metricsSupport;
  private final Namer<TencentBasicResource> namer;
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(APPLICATIONS.ns));
          add(AUTHORITATIVE.forType(LOAD_BALANCERS.ns));
          add(INFORMATIVE.forType(INSTANCES.ns));
        }
      };
  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};
}
