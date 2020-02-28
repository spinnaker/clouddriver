/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.ON_DEMAND;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass.*;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.yandex.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.health.YandexLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class YandexNetworkLoadBalancerCachingAgent extends AbstractYandexCachingAgent
    implements OnDemandAgent {
  private String agentType = getAccountName() + "/" + this.getClass().getSimpleName();
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final OnDemandMetricsSupport metricsSupport;
  private Collection<AgentDataType> providedDataTypes =
      ImmutableSet.of(AUTHORITATIVE.forType(LOAD_BALANCERS.getNs()));

  public YandexNetworkLoadBalancerCachingAgent(
      YandexCloudCredentials credentials, ObjectMapper objectMapper, Registry registry) {
    super(credentials, objectMapper);
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, YandexCloudProvider.ID + ":" + OnDemandType.LoadBalancer);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.LoadBalancer) && cloudProvider.equals(YandexCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(final ProviderCache providerCache, final Map<String, ?> data) {
    String loadBalancerId = (String) data.get("loadBalancerId");
    if (loadBalancerId == null || !getAccountName().equals(data.get("account"))) {
      return null;
    }

    YandexCloudLoadBalancer loadBalancer;
    try {
      loadBalancer = metricsSupport.readData(() -> getLoadBalancer(loadBalancerId));
    } catch (IllegalArgumentException e) {
      return null;
    }

    if (loadBalancer == null) {
      providerCache.evictDeletedItems(
          ON_DEMAND.getNs(),
          singleton(Keys.getLoadBalancerKey(getAccountName(), loadBalancerId, "*", "*")));
      return null;
    }
    String loadBalancerKey =
        Keys.getLoadBalancerKey(
            getAccountName(), loadBalancerId, getFolder(), loadBalancer.getName());

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();
    cacheResultBuilder.setStartTime(Long.MAX_VALUE);
    CacheResult result =
        metricsSupport.transformData(
            () -> buildCacheResult(cacheResultBuilder, singletonList(loadBalancer)));

    metricsSupport.onDemandStore(
        () -> {
          Map<String, Object> attributes = new HashMap<>(4);
          attributes.put("cacheTime", System.currentTimeMillis());
          try {
            attributes.put(
                "cacheResults", getObjectMapper().writeValueAsString(result.getCacheResults()));
          } catch (JsonProcessingException ignored) {

          }
          attributes.put("processedCount", 0);
          attributes.put("processedTime", null);
          DefaultCacheData cacheData =
              new DefaultCacheData(
                  loadBalancerKey, (int) TimeUnit.MINUTES.toSeconds(10), attributes, emptyMap());
          providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
          return null;
        });

    OnDemandResult result1 = new OnDemandResult();
    result1.setSourceAgentType(getOnDemandAgentType());
    result1.setCacheResult(result);
    return result1;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    List<String> ownedKeys =
        providerCache.getIdentifiers(ON_DEMAND.getNs()).stream()
            .filter(this::keyOwnedByThisAgent)
            .collect(toList());

    return providerCache.getAll(ON_DEMAND.getNs(), ownedKeys).stream()
        .map(
            cacheData -> {
              Map<String, String> details = Keys.parse(cacheData.getId());
              Map<String, Object> map = new HashMap<>(5);
              map.put("details", details);
              map.put("moniker", convertOnDemandDetails(details));
              map.put("cacheTime", cacheData.getAttributes().get("cacheTime"));
              map.put("processedCount", cacheData.getAttributes().get("processedCount"));
              map.put("processedTime", cacheData.getAttributes().get("processedTime"));
              return map;
            })
        .collect(toList());
  }

  private boolean keyOwnedByThisAgent(String key) {
    Map<String, String> parsedKey = Keys.parse(key);
    return parsedKey != null && parsedKey.get("type").equals(LOAD_BALANCERS.getNs());
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    final CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();
    cacheResultBuilder.setStartTime(System.currentTimeMillis());

    List<YandexCloudLoadBalancer> loadBalancers = constructLoadBalancers(null);
    List<String> loadBalancerKeys =
        loadBalancers.stream()
            .map(
                lb ->
                    Keys.getLoadBalancerKey(
                        getAccountName(), lb.getId(), getFolder(), lb.getName()))
            .collect(toList());

    providerCache
        .getAll(ON_DEMAND.getNs(), loadBalancerKeys)
        .forEach(
            cacheData -> {
              // Ensure that we don't overwrite data that was inserted by the `handle` method while
              // we retrieved the
              // load balancers. Furthermore, cache data that hasn't been moved to the proper
              // namespace needs to be
              // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
              CacheResultBuilder.CacheMutation onDemand = cacheResultBuilder.getOnDemand();
              if ((Long) cacheData.getAttributes().get("cacheTime")
                      < cacheResultBuilder.getStartTime()
                  && (Long) cacheData.getAttributes().get("processedCount") > 0) {
                onDemand.getToEvict().add(cacheData.getId());
              } else {
                onDemand.getToKeep().put(cacheData.getId(), cacheData);
              }
            });

    CacheResult cacheResults = buildCacheResult(cacheResultBuilder, loadBalancers);
    if (cacheResults.getCacheResults() != null) {
      cacheResults
          .getCacheResults()
          .getOrDefault(ON_DEMAND.getNs(), emptyList())
          .forEach(
              cacheData -> {
                cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
                cacheData
                    .getAttributes()
                    .compute(
                        "processedCount", (key, count) -> (count != null ? (Long) count : 0) + 1);
              });
    }
    return cacheResults;
  }

  private YandexCloudLoadBalancer getLoadBalancer(String loadBalancerId) {
    List<YandexCloudLoadBalancer> loadBalancers = constructLoadBalancers(loadBalancerId);
    return loadBalancers != null && !loadBalancers.isEmpty() ? loadBalancers.get(0) : null;
  }

  private CacheResult buildCacheResult(
      final CacheResultBuilder cacheResultBuilder, List<YandexCloudLoadBalancer> loadBalancers) {
    loadBalancers.forEach(
        loadBalancer -> {
          String loadBalancerKey =
              Keys.getLoadBalancerKey(
                  getAccountName(), loadBalancer.getId(), getFolder(), loadBalancer.getName());

          if (shouldUseOnDemandData(cacheResultBuilder, loadBalancerKey)) {
            try {
              moveOnDemandDataToNamespace(
                  cacheResultBuilder,
                  Keys.getLoadBalancerKey(
                      getAccountName(), loadBalancer.getId(), getFolder(), loadBalancer.getName()));
            } catch (IOException e) {
              // CatsOnDemandCacheUpdater handles this
              throw new UncheckedIOException(e);
            }
          } else {
            CacheResultBuilder.CacheDataBuilder keep =
                cacheResultBuilder.namespace(LOAD_BALANCERS.getNs()).keep(loadBalancerKey);
            keep.setAttributes(getObjectMapper().convertValue(loadBalancer, MAP_TYPE_REFERENCE));
          }
        });
    return cacheResultBuilder.build();
  }

  private static boolean shouldUseOnDemandData(
      CacheResultBuilder cacheResultBuilder, String loadBalancerKey) {
    CacheData cacheData = cacheResultBuilder.getOnDemand().getToKeep().get(loadBalancerKey);
    return cacheData != null
        && (Long) cacheData.getAttributes().get("cacheTime") >= cacheResultBuilder.getStartTime();
  }

  private List<YandexCloudLoadBalancer> constructLoadBalancers(String loadBalancerId) {
    if (loadBalancerId != null) {
      GetNetworkLoadBalancerRequest request =
          GetNetworkLoadBalancerRequest.newBuilder()
              .setNetworkLoadBalancerId(loadBalancerId)
              .build();
      NetworkLoadBalancer networkLoadBalancer =
          getCredentials().networkLoadBalancerService().get(request);
      return singletonList(convertLoadBalancer(networkLoadBalancer));
    } else {
      ListNetworkLoadBalancersResponse response =
          getCredentials()
              .networkLoadBalancerService()
              .list(ListNetworkLoadBalancersRequest.newBuilder().setFolderId(getFolder()).build());
      return response.getNetworkLoadBalancersList().stream()
          .map(this::convertLoadBalancer)
          .collect(Collectors.toList());
    }
  }

  private YandexCloudLoadBalancer convertLoadBalancer(NetworkLoadBalancer networkLoadBalancer) {
    Map<String, List<YandexLoadBalancerHealth>> healths =
        networkLoadBalancer.getAttachedTargetGroupsList().stream()
            .collect(
                toMap(
                    AttachedTargetGroup::getTargetGroupId,
                    tg -> getTargetStates(networkLoadBalancer, tg)));

    return YandexCloudLoadBalancer.createFromNetworkLoadBalancer(
        networkLoadBalancer, getAccountName(), healths);
  }

  private List<YandexLoadBalancerHealth> getTargetStates(
      NetworkLoadBalancer networkLoadBalancer, AttachedTargetGroup tg) {
    GetTargetStatesRequest request =
        GetTargetStatesRequest.newBuilder()
            .setNetworkLoadBalancerId(networkLoadBalancer.getId())
            .setTargetGroupId(tg.getTargetGroupId())
            .build();
    List<TargetState> targetStatesList =
        getCredentials()
            .networkLoadBalancerService()
            .getTargetStates(request)
            .getTargetStatesList();
    return targetStatesList.stream()
        .map(
            state ->
                new YandexLoadBalancerHealth(
                    state.getAddress(),
                    state.getSubnetId(),
                    YandexLoadBalancerHealth.Status.valueOf(state.getStatus().name())))
        .collect(toList());
  }
}
