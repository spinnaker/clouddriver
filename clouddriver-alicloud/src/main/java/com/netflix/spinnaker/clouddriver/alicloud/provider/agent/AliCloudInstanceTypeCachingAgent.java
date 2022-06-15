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
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone.AvailableResource;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone.AvailableResource.SupportedResource;
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

public class AliCloudInstanceTypeCachingAgent implements CachingAgent {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudInstanceTypeCachingAgent(
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
              add(AUTHORITATIVE.forType(Keys.Namespace.INSTANCE_TYPES.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {

    List<AvailableZone> zones = findAllAvailableResources();
    if (zones == null) {
      return new DefaultCacheResult(new HashMap<>(16));
    }

    List<CacheData> instanceTypeDatas =
        zones.stream()
            .map(
                availableZone -> {
                  String regionId = availableZone.getBizRegionId();
                  String zoneId = availableZone.getZoneId();
                  List<String> names = findAvailableResourceNames(availableZone);

                  Map<String, Object> attributes = new HashMap<>(20);
                  attributes.put("provider", AliCloudProvider.ID);
                  attributes.put("account", account.getName());
                  attributes.put("regionId", regionId);
                  attributes.put("zoneId", zoneId);
                  attributes.put("names", names);
                  return (CacheData)
                      new DefaultCacheData(
                          Keys.getInstanceTypeKey(account.getName(), region, zoneId),
                          attributes,
                          new HashMap<>(16));
                })
            .collect(Collectors.toList());

    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    resultMap.put(Keys.Namespace.INSTANCE_TYPES.ns, instanceTypeDatas);
    return new DefaultCacheResult(resultMap);
  }

  private List<String> findAvailableResourceNames(AvailableZone availableZone) {
    if (!"Available".equals(availableZone.getStatus())) {
      return Collections.emptyList();
    }
    if ("WithoutStock".equals(availableZone.getStatusCategory())) {
      return Collections.emptyList();
    }
    return availableZone.getAvailableResources().stream()
        .filter(r -> "InstanceType".equals(r.getType()))
        .map(AvailableResource::getSupportedResources)
        .flatMap(Collection::stream)
        .filter(supportedResource -> "Available".equals(supportedResource.getStatus()))
        .filter(supportedResource -> !"WithoutStock".equals(supportedResource.getStatusCategory()))
        .map(SupportedResource::getValue)
        .collect(Collectors.toList());
  }

  private List<AvailableZone> findAllAvailableResources() {

    DescribeAvailableResourceRequest describeZonesRequest = new DescribeAvailableResourceRequest();
    describeZonesRequest.setDestinationResource("InstanceType");
    describeZonesRequest.setInstanceChargeType("PostPaid");
    describeZonesRequest.setIoOptimized("optimized");
    describeZonesRequest.setResourceType("instance");

    DescribeAvailableResourceResponse describeZonesResponse;
    try {
      describeZonesResponse = client.getAcsResponse(describeZonesRequest);
      return describeZonesResponse.getAvailableZones();

    } catch (ClientException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AliProvider.PROVIDER_NAME;
  }
}
