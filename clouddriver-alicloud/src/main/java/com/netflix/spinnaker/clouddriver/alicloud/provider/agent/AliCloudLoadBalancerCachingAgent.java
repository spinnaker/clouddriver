/*
 * Copyright 2019 Alibaba Group.
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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.slb.model.v20140515.*;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse.ListenerPortAndProtocal;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse.LoadBalancer;
import com.aliyuncs.slb.model.v20140515.DescribeVServerGroupsResponse.VServerGroup;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancerType;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudClientProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentialsProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class AliCloudLoadBalancerCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {

  private AliProvider aliProvider;
  private AliCloudClientProvider aliCloudClientProvider;
  private AliCloudCredentials account;
  private String region;
  private AliCloudCredentialsProvider aliCloudCredentialsProvider;
  ObjectMapper objectMapper;
  OnDemandMetricsSupport metricsSupport;
  IAcsClient client;

  public AliCloudLoadBalancerCachingAgent(
      AliProvider aliProvider,
      String region,
      AliCloudClientProvider aliCloudClientProvider,
      AliCloudCredentialsProvider aliCloudCredentialsProvider,
      AliCloudProvider aliCloudProvider,
      ObjectMapper objectMapper,
      Registry registry,
      AliCloudCredentials credentials,
      IAcsClient client) {
    this.account = credentials;
    this.aliProvider = aliProvider;
    this.region = region;
    this.aliCloudClientProvider = aliCloudClientProvider;
    this.aliCloudCredentialsProvider = aliCloudCredentialsProvider;
    this.objectMapper = objectMapper;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            aliCloudProvider.getId()
                + ":"
                + aliCloudProvider.getId()
                + ":"
                + OnDemandType.LoadBalancer);
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<>() {
            {
              add(AUTHORITATIVE.forType(LOAD_BALANCERS.ns));
              add(INFORMATIVE.forType(INSTANCES.ns));
            }
          });

  @Override
  public String getAccountName() {
    return account.getName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    DescribeLoadBalancersResponse queryResponse = null;
    try {
      DescribeLoadBalancersRequest queryRequest = new DescribeLoadBalancersRequest();
      queryResponse = client.getAcsResponse(queryRequest);
    } catch (ClientException e) {
      e.printStackTrace();
    }

    if (queryResponse == null
        || queryResponse.getLoadBalancers() == null
        || queryResponse.getLoadBalancers().isEmpty()) {
      return new DefaultCacheResult(new HashMap<>(16));
    }

    return buildCacheResult(queryResponse);
  }

  @NotNull
  private DefaultCacheResult buildCacheResult(DescribeLoadBalancersResponse queryResponse) {
    List defaultCacheDatas =
        queryResponse.getLoadBalancers().stream()
            .map(this::getLoadBalancerCacheBuilder)
            .map(detail -> detail.buildLoadBalancerCache(objectMapper))
            .collect(Collectors.toList());

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>(16);
    cacheResults.put(LOAD_BALANCERS.ns, defaultCacheDatas);
    return new DefaultCacheResult(cacheResults);
  }

  @NotNull
  private LoadBalancerCacheBuilder getLoadBalancerCacheBuilder(LoadBalancer loadBalancer) {
    String loadBalancerId = loadBalancer.getLoadBalancerId();

    DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
        new DescribeLoadBalancerAttributeRequest();
    describeLoadBalancerAttributeRequest.setLoadBalancerId(loadBalancerId);
    DescribeLoadBalancerAttributeResponse loadBalancerAttribute = null;
    List<Map> listenerAttributes = null;
    try {
      loadBalancerAttribute = client.getAcsResponse(describeLoadBalancerAttributeRequest);
      List<ListenerPortAndProtocal> listenerPortsAndProtocal =
          loadBalancerAttribute.getListenerPortsAndProtocal();

      listenerAttributes =
          listenerPortsAndProtocal.stream()
              .map(
                  portAndProtocal ->
                      describeLoadBalancerListenerAttribute(loadBalancerId, portAndProtocal))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
    } catch (ClientException e) {
      e.printStackTrace();
    }

    List<DescribeVServerGroupsResponse.VServerGroup> vServerGroups = null;
    try {
      DescribeVServerGroupsRequest describeVServerGroupsRequest =
          new DescribeVServerGroupsRequest();
      describeVServerGroupsRequest.setLoadBalancerId(loadBalancerId);
      DescribeVServerGroupsResponse describeVServerGroupsResponse =
          client.getAcsResponse(describeVServerGroupsRequest);
      vServerGroups = describeVServerGroupsResponse.getVServerGroups();
    } catch (ClientException e) {
      e.printStackTrace();
    }

    return new LoadBalancerCacheBuilder(
        loadBalancer,
        loadBalancerAttribute,
        listenerAttributes,
        vServerGroups,
        account.getName(),
        region);
  }

  private Map<String, Object> describeLoadBalancerListenerAttribute(
      String loadBalancerId, ListenerPortAndProtocal portAndProtocal) {
    Integer listenerPort = portAndProtocal.getListenerPort();
    String listenerProtocal = portAndProtocal.getListenerProtocal().toUpperCase();
    switch (listenerProtocal) {
      case "HTTPS":
        DescribeLoadBalancerHTTPSListenerAttributeRequest httpsListenerAttributeRequest =
            new DescribeLoadBalancerHTTPSListenerAttributeRequest();
        httpsListenerAttributeRequest.setListenerPort(listenerPort);
        httpsListenerAttributeRequest.setLoadBalancerId(loadBalancerId);
        DescribeLoadBalancerHTTPSListenerAttributeResponse httpsListenerAttributeResponse;
        try {
          httpsListenerAttributeResponse = client.getAcsResponse(httpsListenerAttributeRequest);
          return objectMapper.convertValue(httpsListenerAttributeResponse, Map.class);
        } catch (ClientException e) {
          e.printStackTrace();
        }
        break;
      case "TCP":
        DescribeLoadBalancerTCPListenerAttributeRequest tcpListenerAttributeRequest =
            new DescribeLoadBalancerTCPListenerAttributeRequest();
        tcpListenerAttributeRequest.setListenerPort(listenerPort);
        tcpListenerAttributeRequest.setLoadBalancerId(loadBalancerId);
        DescribeLoadBalancerTCPListenerAttributeResponse tcpListenerAttributeResponse;
        try {
          tcpListenerAttributeResponse = client.getAcsResponse(tcpListenerAttributeRequest);
          return objectMapper.convertValue(tcpListenerAttributeResponse, Map.class);
        } catch (ClientException e) {
          e.printStackTrace();
        }

        break;
      case "UDP":
        DescribeLoadBalancerUDPListenerAttributeRequest udpListenerAttributeRequest =
            new DescribeLoadBalancerUDPListenerAttributeRequest();
        udpListenerAttributeRequest.setListenerPort(listenerPort);
        udpListenerAttributeRequest.setLoadBalancerId(loadBalancerId);
        DescribeLoadBalancerUDPListenerAttributeResponse udpListenerAttributeResponse;
        try {
          udpListenerAttributeResponse = client.getAcsResponse(udpListenerAttributeRequest);
          return objectMapper.convertValue(udpListenerAttributeResponse, Map.class);
        } catch (ClientException e) {
          e.printStackTrace();
        }

        break;
      default:
        DescribeLoadBalancerHTTPListenerAttributeRequest httpListenerAttributeRequest =
            new DescribeLoadBalancerHTTPListenerAttributeRequest();
        httpListenerAttributeRequest.setListenerPort(listenerPort);
        httpListenerAttributeRequest.setLoadBalancerId(loadBalancerId);
        DescribeLoadBalancerHTTPListenerAttributeResponse httpListenerAttributeResponse;
        try {
          httpListenerAttributeResponse = client.getAcsResponse(httpListenerAttributeRequest);
          return objectMapper.convertValue(httpListenerAttributeResponse, Map.class);
        } catch (ClientException e) {
          e.printStackTrace();
        }
        break;
    }
    return null;
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    List<DescribeLoadBalancersResponse.LoadBalancer> loadBalancers = new ArrayList<>();
    Map<String, DescribeLoadBalancerAttributeResponse> loadBalancerAttributes = new HashMap<>(16);

    DescribeLoadBalancersRequest queryRequest = new DescribeLoadBalancersRequest();
    queryRequest.setLoadBalancerName((String) data.get("loadBalancerName"));
    DescribeLoadBalancersResponse queryResponse;
    String loadBalancerId = null;

    queryResponse =
        metricsSupport.readData(
            () -> {
              try {
                return client.getAcsResponse(queryRequest);
              } catch (ServerException e) {
                e.printStackTrace();
              } catch (ClientException e) {
                e.printStackTrace();
              }
              return null;
            });

    loadBalancers.addAll(queryResponse.getLoadBalancers());

    if (StringUtils.isEmpty(loadBalancerId)) {
      return null;
    }

    DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
        new DescribeLoadBalancerAttributeRequest();
    describeLoadBalancerAttributeRequest.setLoadBalancerId(loadBalancerId);
    DescribeLoadBalancerAttributeResponse describeLoadBalancerAttributeResponse;
    describeLoadBalancerAttributeResponse =
        metricsSupport.readData(
            () -> {
              try {
                return client.getAcsResponse(describeLoadBalancerAttributeRequest);

              } catch (ServerException e) {
                e.printStackTrace();
              } catch (ClientException e) {
                e.printStackTrace();
              }
              return null;
            });

    CacheResult cacheResult = buildCacheResult(queryResponse);

    if (cacheResult.getCacheResults().values().isEmpty()) {
      providerCache.evictDeletedItems(
          ON_DEMAND.ns,
          Lists.newArrayList(
              Keys.getLoadBalancerKey(
                  (String) data.get("loadBalancerName"),
                  account.getName(),
                  region,
                  (String) data.get("vpcId"),
                  AliCloudLoadBalancerType.CLB.ns)));
    } else {
      metricsSupport.onDemandStore(
          () -> {
            Map<String, Object> map = Maps.newHashMap();
            map.put("cacheTime", new Date());
            try {
              map.put(
                  "cacheResults", objectMapper.writeValueAsString(cacheResult.getCacheResults()));
            } catch (JsonProcessingException exception) {
              exception.printStackTrace();
            }

            CacheData cacheData =
                new DefaultCacheData(
                    Keys.getLoadBalancerKey(
                        (String) data.get("loadBalancerName"),
                        account.getName(),
                        region,
                        (String) data.get("vpcId"),
                        AliCloudLoadBalancerType.CLB.ns),
                    map,
                    Maps.newHashMap());

            providerCache.putCacheData(ON_DEMAND.ns, cacheData);
            return null;
          });
    }

    OnDemandResult result = new OnDemandResult(getAgentType(), cacheResult, null);

    return result;
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
  public String getOnDemandAgentType() {
    return this.getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return null;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return false;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return null;
  }

  @Data
  public static class LoadBalancerCacheBuilder {
    final LoadBalancer loadBalancer;
    final DescribeLoadBalancerAttributeResponse loadBalancerAttribute;
    final List<Map> listenerAttributes;
    final List<DescribeVServerGroupsResponse.VServerGroup> vServerGroups;
    final String account;
    final String region;

    public LoadBalancerCacheBuilder(
        LoadBalancer loadBalancer,
        DescribeLoadBalancerAttributeResponse loadBalancerAttribute,
        List<Map> listenerAttributes,
        List<VServerGroup> vServerGroups,
        String account,
        String region) {
      this.loadBalancer = loadBalancer;
      this.loadBalancerAttribute = loadBalancerAttribute;
      this.listenerAttributes = listenerAttributes;
      this.vServerGroups = vServerGroups;
      this.account = account;
      this.region = region;
    }

    public DefaultCacheData buildLoadBalancerCache(ObjectMapper objectMapper) {
      LoadBalancer loadBalancer = this.loadBalancer;
      Map<String, Object> map = objectMapper.convertValue(loadBalancer, Map.class);
      map.put("account", this.account);
      map.put("loadBalancerType", AliCloudLoadBalancerType.CLB.ns);
      Map<String, Object> attributeMap =
          objectMapper.convertValue(this.loadBalancerAttribute, Map.class);
      attributeMap.put("listenerPortsAndProtocal", this.listenerAttributes);
      map.put("attributes", attributeMap);
      map.put("vServerGroups", this.vServerGroups);

      return new DefaultCacheData(
          Keys.getLoadBalancerKey(
              loadBalancer.getLoadBalancerName(),
              this.account,
              this.region,
              loadBalancer.getVpcId(),
              AliCloudLoadBalancerType.CLB.ns),
          map,
          Maps.newHashMap());
    }

    public static AliCloudLoadBalancer buildLoadBalancer(
        CacheData loadBalancerCacheData, ObjectMapper objectMapper) {
      Map<String, Object> attributes =
          objectMapper.convertValue(loadBalancerCacheData.getAttributes(), Map.class);
      return new AliCloudLoadBalancer(
          String.valueOf(attributes.get("account")),
          String.valueOf(attributes.get("regionIdAlias")),
          String.valueOf(attributes.get("loadBalancerName")),
          String.valueOf(attributes.get("vpcId")),
          String.valueOf(attributes.get("loadBalancerId")));
    }
  }
}
