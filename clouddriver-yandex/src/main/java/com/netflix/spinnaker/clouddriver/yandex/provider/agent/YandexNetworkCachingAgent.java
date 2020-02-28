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
import static java.util.Collections.*;
import static yandex.cloud.api.vpc.v1.NetworkOuterClass.Network;
import static yandex.cloud.api.vpc.v1.NetworkServiceOuterClass.ListNetworksRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudNetwork;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class YandexNetworkCachingAgent extends AbstractYandexCachingAgent {
  private String agentType =
      getAccountName() + "/" + YandexNetworkCachingAgent.class.getSimpleName();
  private Set<AgentDataType> providedDataTypes =
      singleton(AUTHORITATIVE.forType(Keys.Namespace.NETWORKS.getNs()));

  public YandexNetworkCachingAgent(YandexCloudCredentials credentials, ObjectMapper objectMapper) {
    super(credentials, objectMapper);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    ListNetworksRequest request = ListNetworksRequest.newBuilder().setFolderId(getFolder()).build();
    List<Network> networkList = getCredentials().networkService().list(request).getNetworksList();

    Collection<CacheData> cacheData =
        networkList.stream()
            .map(
                network ->
                    new DefaultCacheData(
                        Keys.getNetworkKey(
                            getAccountName(),
                            network.getId(),
                            network.getFolderId(),
                            network.getName()),
                        getObjectMapper()
                            .convertValue(
                                YandexCloudNetwork.createFromProto(network, getAccountName()),
                                MAP_TYPE_REFERENCE),
                        emptyMap()))
            .collect(Collectors.toList());

    return new DefaultCacheResult(singletonMap(Keys.Namespace.NETWORKS.getNs(), cacheData));
  }
}
