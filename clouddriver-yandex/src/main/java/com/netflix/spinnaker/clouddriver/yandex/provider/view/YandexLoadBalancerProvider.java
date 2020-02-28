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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.model.health.YandexLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Data
public class YandexLoadBalancerProvider implements LoadBalancerProvider<YandexCloudLoadBalancer> {
  private final String cloudProvider = YandexCloudProvider.ID;
  private Cache cacheView;
  private ObjectMapper objectMapper;

  @Autowired
  public YandexLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<YandexCloudLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String pattern = Keys.getLoadBalancerKey("*", "*", "*", applicationName + "*");
    String balancersNs = LOAD_BALANCERS.getNs();
    Collection<String> identifiers = cacheView.filterIdentifiers(balancersNs, pattern);

    if (!Strings.isNullOrEmpty(applicationName)) {
      Collection<CacheData> applicationServerGroups =
          cacheView.getAll(
              SERVER_GROUPS.getNs(),
              cacheView.filterIdentifiers(
                  SERVER_GROUPS.getNs(),
                  Keys.getServerGroupKey("*", "*", "*", applicationName + "*")));
      applicationServerGroups.stream()
          .map(CacheData::getRelationships)
          .filter(Objects::nonNull)
          .map(relationships -> relationships.get(balancersNs))
          .filter(Objects::nonNull)
          .forEach(identifiers::addAll);
    }
    RelationshipCacheFilter cacheFilter =
        RelationshipCacheFilter.include(SERVER_GROUPS.getNs(), INSTANCES.getNs());
    return cacheView.getAll(balancersNs, identifiers, cacheFilter).stream()
        .map(this::loadBalancersFromCacheData)
        .collect(toSet());
  }

  private YandexCloudLoadBalancer loadBalancersFromCacheData(CacheData cacheData) {
    YandexCloudLoadBalancer loadBalancer =
        objectMapper.convertValue(cacheData.getAttributes(), YandexCloudLoadBalancer.class);

    Collection<String> serverGroupKeys =
        cacheData.getRelationships() == null
            ? emptySet()
            : cacheData.getRelationships().getOrDefault(SERVER_GROUPS.getNs(), emptySet());
    if (serverGroupKeys.isEmpty()) {
      return loadBalancer;
    }

    cacheView
        .getAll(SERVER_GROUPS.getNs(), serverGroupKeys)
        .forEach(
            sgCacheData -> {
              YandexCloudServerGroup serverGroup =
                  objectMapper.convertValue(
                      sgCacheData.getAttributes(), YandexCloudServerGroup.class);

              LoadBalancerServerGroup loadBalancerServerGroup = new LoadBalancerServerGroup();

              loadBalancerServerGroup.setCloudProvider(YandexCloudProvider.ID);
              loadBalancerServerGroup.setName(serverGroup.getName());
              loadBalancerServerGroup.setRegion(serverGroup.getRegion());
              loadBalancerServerGroup.setIsDisabled(serverGroup.isDisabled());
              loadBalancerServerGroup.setDetachedInstances(emptySet());
              loadBalancerServerGroup.setInstances(
                  serverGroup.getInstances().stream()
                      .map(
                          instance ->
                              new LoadBalancerInstance(
                                  instance.getId(),
                                  instance.getName(),
                                  instance.getZone(),
                                  singletonMap(
                                      "state",
                                      loadBalancer
                                          .getHealths()
                                          .getOrDefault(
                                              serverGroup
                                                  .getLoadBalancerIntegration()
                                                  .getTargetGroupId(),
                                              emptyList())
                                          .stream()
                                          .filter(
                                              health ->
                                                  instance
                                                      .getAddressesInSubnets()
                                                      .get(health.getSubnetId())
                                                      .contains(health.getAddress()))
                                          .findFirst()
                                          .map(health -> health.getStatus().toServiceStatus())
                                          .orElseGet(
                                              () ->
                                                  YandexLoadBalancerHealth.Status.ServiceStatus
                                                      .OutOfService))))
                      .collect(toSet()));

              loadBalancer.getServerGroups().add(loadBalancerServerGroup);
            });

    return loadBalancer;
  }

  public List<YandexLoadBalancerAccountRegionSummary> list() {
    Map<String, List<YandexCloudLoadBalancer>> loadBalancerMap =
        getApplicationLoadBalancers("").stream()
            .collect(groupingBy(YandexCloudLoadBalancer::getName));

    return loadBalancerMap.entrySet().stream()
        .map(e -> convertToSummary(e.getKey(), e.getValue()))
        .collect(toList());
  }

  @NotNull
  YandexLoadBalancerProvider.YandexLoadBalancerAccountRegionSummary convertToSummary(
      String key, List<YandexCloudLoadBalancer> balancers) {
    YandexLoadBalancerAccountRegionSummary summary = new YandexLoadBalancerAccountRegionSummary();
    summary.setName(key);
    balancers.forEach(
        balancer -> {
          YandexLoadBalancerSummary s = new YandexLoadBalancerSummary();
          s.setId(balancer.getId());
          s.setAccount(balancer.getAccount());
          s.setName(balancer.getName());
          s.setRegion(balancer.getRegion());

          summary
              .getMappedAccounts()
              .computeIfAbsent(balancer.getAccount(), a -> new YandexLoadBalancerAccount())
              .getMappedRegions()
              .computeIfAbsent(balancer.getRegion(), a -> new YandexLoadBalancerAccountRegion())
              .getLoadBalancers()
              .add(s);
        });

    return summary;
  }

  public YandexLoadBalancerAccountRegionSummary get(String name) {
    Map<String, List<YandexCloudLoadBalancer>> loadBalancerMap =
        getApplicationLoadBalancers("").stream()
            .collect(groupingBy(YandexCloudLoadBalancer::getName));
    if (!loadBalancerMap.containsKey(name)) {
      return null;
    }
    return convertToSummary(name, loadBalancerMap.get(name));
  }

  public List<YandexLoadBalancerDetails> byAccountAndRegionAndName(
      final String account, final String region, String name) {
    Set<YandexCloudLoadBalancer> balancers = getApplicationLoadBalancers(name);
    return balancers.stream()
        .filter(
            balancer ->
                balancer.getAccount().equals(account) && balancer.getRegion().equals(region))
        .map(
            balancer ->
                new YandexLoadBalancerDetails(
                    balancer.getName(),
                    balancer.getBalancerType(),
                    balancer.getSessionAffinity().name(),
                    balancer.getCreatedTime(),
                    balancer.getListeners()))
        .findFirst()
        .map(Collections::singletonList)
        .orElseGet(Collections::emptyList);
  }

  @Data
  public static class YandexLoadBalancerAccountRegionSummary implements LoadBalancerProvider.Item {
    private String name;

    @JsonIgnore private Map<String, YandexLoadBalancerAccount> mappedAccounts = new HashMap<>();

    @JsonProperty("accounts")
    public List<YandexLoadBalancerAccount> getByAccounts() {
      return new ArrayList<>(mappedAccounts.values());
    }
  }

  @Data
  public static class YandexLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private String name;

    @JsonIgnore
    private Map<String, YandexLoadBalancerAccountRegion> mappedRegions = new HashMap<>();

    @JsonProperty("regions")
    public List<YandexLoadBalancerAccountRegion> getByRegions() {
      return new ArrayList<>(mappedRegions.values());
    }
  }

  @Data
  private static class YandexLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {
    private String name;
    private List<YandexLoadBalancerSummary> loadBalancers = new ArrayList<>();
  }

  @Data
  private static class YandexLoadBalancerSummary implements LoadBalancerProvider.Details {
    private String id;
    private String account;
    private String region;
    private String name;
    private String type = YandexCloudProvider.ID;
  }

  @Data
  @AllArgsConstructor
  private static class YandexLoadBalancerDetails implements LoadBalancerProvider.Details {
    private String loadBalancerName;
    YandexCloudLoadBalancer.BalancerType type;
    private String sessionAffinity;
    private Long createdTime;
    private List<YandexCloudLoadBalancer.Listener> listeners;
  }
}
