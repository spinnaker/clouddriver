/*
 * Copyright 2022 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.exceptions.ClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AliCloudKeyPairCachingAgent implements CachingAgent {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudKeyPairCachingAgent(
      AliCloudCredentials account, String region, ObjectMapper objectMapper, IAcsClient client) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<>() {
            {
              add(AUTHORITATIVE.forType(Keys.Namespace.ALI_CLOUD_KEY_PAIRS.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {

    List<KeyPair> keyPairs = findKeyPairs();
    if (keyPairs == null) {
      return new DefaultCacheResult(new HashMap<>(16));
    }

    List<CacheData> keyPairDatas =
        keyPairs.stream()
            .map(
                keyPair -> {
                  Map<String, Object> attributes = objectMapper.convertValue(keyPair, Map.class);
                  attributes.put("provider", AliCloudProvider.ID);
                  attributes.put("account", account.getName());
                  attributes.put("regionId", region);
                  return new DefaultCacheData(
                      Keys.getKeyPairKey(keyPair.getKeyPairName(), region, account.getName()),
                      attributes,
                      new HashMap<>(16));
                })
            .collect(Collectors.toList());

    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    resultMap.put(Keys.Namespace.ALI_CLOUD_KEY_PAIRS.ns, keyPairDatas);
    return new DefaultCacheResult(resultMap);
  }

  private List<KeyPair> findKeyPairs() {
    DescribeKeyPairsRequest keyPairsRequest = new DescribeKeyPairsRequest();
    keyPairsRequest.setPageSize(50);
    DescribeKeyPairsResponse keyPairsResponse;
    try {
      keyPairsResponse = client.getAcsResponse(keyPairsRequest);
      return keyPairsResponse.getKeyPairs();
    } catch (ClientException e) {
      e.printStackTrace();
    }
    return Collections.emptyList();
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AliProvider.PROVIDER_NAME;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }
}
