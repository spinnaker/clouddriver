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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.*;
import static java.util.Collections.*;
import static yandex.cloud.api.compute.v1.InstanceOuterClass.Instance;
import static yandex.cloud.api.compute.v1.InstanceServiceOuterClass.ListInstancesRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class YandexInstanceCachingAgent extends AbstractYandexCachingAgent {
  private String agentType =
      getAccountName() + "/" + YandexInstanceCachingAgent.class.getSimpleName();
  private Set<AgentDataType> providedDataTypes =
      new HashSet<>(
          Arrays.asList(
              AUTHORITATIVE.forType(INSTANCES.getNs()),
              INFORMATIVE.forType(CLUSTERS.getNs()),
              INFORMATIVE.forType(LOAD_BALANCERS.getNs())));

  public YandexInstanceCachingAgent(YandexCloudCredentials credentials, ObjectMapper objectMapper) {
    super(credentials, objectMapper);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    ListInstancesRequest request =
        ListInstancesRequest.newBuilder().setFolderId(getFolder()).build();
    List<Instance> instancesList =
        getCredentials().instanceService().list(request).getInstancesList();

    Collection<CacheData> cacheData =
        instancesList.stream()
            .map(YandexCloudInstance::createFromProto)
            .peek(instance -> linkWithLoadBalancers(instance, providerCache))
            .map(this::buildCacheResults)
            .collect(Collectors.toList());

    return new DefaultCacheResult(singletonMap(INSTANCES.getNs(), cacheData));
  }

  private void linkWithLoadBalancers(YandexCloudInstance instance, ProviderCache providerCache) {
    Collection<String> identifiers =
        providerCache.filterIdentifiers(
            LOAD_BALANCERS.getNs(), Keys.getLoadBalancerKey("*", "*", "*", "*"));

    providerCache.getAll(LOAD_BALANCERS.getNs(), identifiers).stream()
        .map(
            cacheData ->
                getObjectMapper()
                    .convertValue(cacheData.getAttributes(), YandexCloudLoadBalancer.class))
        .filter(
            balancer ->
                balancer.getHealths().values().stream()
                    .flatMap(Collection::stream)
                    .anyMatch(
                        health ->
                            instance
                                .getAddressesInSubnets()
                                .getOrDefault(health.getSubnetId(), emptyList())
                                .contains(health.getAddress())))
        .forEach(instance::linkWithLoadBalancer);
  }

  private CacheData buildCacheResults(YandexCloudInstance instance) {
    String defaultName =
        Strings.isNullOrEmpty(instance.getName()) ? instance.getId() : instance.getName();
    String applicationName =
        instance.getLabels().getOrDefault("spinnaker-application", defaultName);
    String clusterKey =
        Keys.getClusterKey(
            getAccountName(),
            applicationName,
            instance.getLabels().getOrDefault("spinnaker-cluster", defaultName));
    String instanceKey =
        Keys.getInstanceKey(getAccountName(), instance.getId(), getFolder(), instance.getName());
    Map<String, Object> attributes = getObjectMapper().convertValue(instance, MAP_TYPE_REFERENCE);
    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(CLUSTERS.getNs(), singletonList(clusterKey));
    relationships.put(APPLICATIONS.getNs(), singletonList(Keys.getApplicationKey(applicationName)));
    return new DefaultCacheData(instanceKey, attributes, relationships);
  }
}
