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
import static yandex.cloud.api.vpc.v1.SubnetOuterClass.Subnet;
import static yandex.cloud.api.vpc.v1.SubnetServiceOuterClass.ListSubnetsRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudSubnet;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class YandexSubnetCachingAgent extends AbstractYandexCachingAgent {
  private String agentType =
      getAccountName() + "/" + YandexSubnetCachingAgent.class.getSimpleName();
  private Set<AgentDataType> providedDataTypes =
      singleton(AUTHORITATIVE.forType(Keys.Namespace.SUBNETS.getNs()));

  public YandexSubnetCachingAgent(YandexCloudCredentials credentials, ObjectMapper objectMapper) {
    super(credentials, objectMapper);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    ListSubnetsRequest request = ListSubnetsRequest.newBuilder().setFolderId(getFolder()).build();
    List<Subnet> subnetList = getCredentials().subnetService().list(request).getSubnetsList();

    Collection<CacheData> cacheData =
        subnetList.stream()
            .map(
                subnet ->
                    new DefaultCacheData(
                        Keys.getSubnetKey(
                            getAccountName(),
                            subnet.getId(),
                            subnet.getFolderId(),
                            subnet.getName()),
                        getObjectMapper()
                            .convertValue(
                                YandexCloudSubnet.createFromProto(subnet, getAccountName()),
                                MAP_TYPE_REFERENCE),
                        emptyMap()))
            .collect(Collectors.toList());

    return new DefaultCacheResult(singletonMap(Keys.Namespace.SUBNETS.getNs(), cacheData));
  }
}
