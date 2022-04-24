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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup.VServerGroup;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse.ScalingInstance;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
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
import com.netflix.spinnaker.clouddriver.alicloud.common.CacheDataHelper;
import com.netflix.spinnaker.clouddriver.alicloud.common.Sets;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

public class AliCloudClusterCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {

  private AliCloudCredentials account;
  private String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudClusterCachingAgent(
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
              add(AUTHORITATIVE.forType(CLUSTERS.ns));
              add(AUTHORITATIVE.forType(SERVER_GROUPS.ns));
              add(AUTHORITATIVE.forType(APPLICATIONS.ns));
              add(INFORMATIVE.forType(LAUNCH_CONFIGS.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    CacheResult result = new DefaultCacheResult(new HashMap<>(16));
    try {
      List<ClusterCachingBuilder> sgData = loadSgDatas();

      result = buildCacheResult(sgData);
    } catch (Exception e) {
      e.printStackTrace();
    }

    return result;
  }

  @NotNull
  private List<ClusterCachingBuilder> loadSgDatas() throws ClientException {
    List<ScalingGroup> scalingGroups = this.findAllScalingGroups();
    List<ClusterCachingBuilder> sgData = new ArrayList<>();
    for (ScalingGroup sg : scalingGroups) {
      String scalingGroupId = sg.getScalingGroupId();
      String activeScalingConfigurationId = sg.getActiveScalingConfigurationId();

      DescribeScalingConfigurationsResponse scalingConfigurationsResponse =
          findScalingConfigurations(activeScalingConfigurationId, scalingGroupId);
      String securityGroupName =
          findSecurityGroupName(scalingConfigurationsResponse.getScalingConfigurations());
      List<DescribeLoadBalancerAttributeResponse> loadBalancerAttributes =
          findLoadBalancerAttributes(sg);
      List<ScalingInstance> scalingInstances =
          findAllScalingInstances(scalingGroupId, activeScalingConfigurationId);

      sgData.add(
          new ClusterCachingBuilder(
              sg,
              account.getName(),
              region,
              scalingConfigurationsResponse,
              loadBalancerAttributes,
              scalingInstances,
              securityGroupName));
    }
    return sgData;
  }

  private List<ScalingGroup> findAllScalingGroups() throws ClientException {
    int pageNumber = 1;
    int pageSize = 50;
    List<ScalingGroup> scalingGroups = new ArrayList<>();

    DescribeScalingGroupsRequest describeScalingGroupsRequest = new DescribeScalingGroupsRequest();
    DescribeScalingGroupsResponse describeScalingGroupsResponse;
    while (true) {
      describeScalingGroupsRequest.setPageSize(pageSize);
      describeScalingGroupsRequest.setPageNumber(pageNumber);
      describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
      if (CollectionUtils.isEmpty(describeScalingGroupsResponse.getScalingGroups())) {
        break;
      } else {
        pageNumber = pageNumber + 1;
        scalingGroups.addAll(describeScalingGroupsResponse.getScalingGroups());
      }
      if (describeScalingGroupsResponse.getScalingGroups().size() < pageSize) {
        break;
      }
    }
    return scalingGroups;
  }

  private DescribeScalingConfigurationsResponse findScalingConfigurations(
      String activeScalingConfigurationId, String scalingGroupId) throws ClientException {
    DescribeScalingConfigurationsRequest scalingConfigurationsRequest =
        new DescribeScalingConfigurationsRequest();
    scalingConfigurationsRequest.setScalingGroupId(scalingGroupId);
    scalingConfigurationsRequest.setScalingConfigurationId1(activeScalingConfigurationId);
    return client.getAcsResponse(scalingConfigurationsRequest);
  }

  private String findSecurityGroupName(List<ScalingConfiguration> scalingConfigurations)
      throws ClientException {
    if (scalingConfigurations.size() > 0) {
      ScalingConfiguration scalingConfiguration = scalingConfigurations.get(0);
      String securityGroupId = scalingConfiguration.getSecurityGroupId();
      DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest();
      securityGroupsRequest.setSecurityGroupId(securityGroupId);
      DescribeSecurityGroupsResponse securityGroupsResponse =
          client.getAcsResponse(securityGroupsRequest);
      if (securityGroupsResponse.getSecurityGroups().size() > 0) {
        SecurityGroup securityGroup = securityGroupsResponse.getSecurityGroups().get(0);
        return securityGroup.getSecurityGroupName();
      }
    }
    return "";
  }

  private List<DescribeLoadBalancerAttributeResponse> findLoadBalancerAttributes(ScalingGroup sg) {
    List<String> loadBalancerIds = sg.getLoadBalancerIds();
    if (sg.getVServerGroups() != null) {
      loadBalancerIds.addAll(
          sg.getVServerGroups().stream()
              .map(VServerGroup::getLoadBalancerId)
              .filter(loadBalancerId -> !loadBalancerIds.contains(loadBalancerId))
              .collect(Collectors.toList()));
    }

    return loadBalancerIds.stream()
        .map(
            loadBalancerId -> {
              try {
                DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
                    new DescribeLoadBalancerAttributeRequest();
                describeLoadBalancerAttributeRequest.setLoadBalancerId(loadBalancerId);
                return client.getAcsResponse(describeLoadBalancerAttributeRequest);
              } catch (ClientException e) {
                String message = e.getMessage();
                if (message.contains("InvalidLoadBalancerId.NotFound")) {
                  logger.info(loadBalancerId + " -> NotFound");
                } else {
                  throw new IllegalStateException(e.getMessage());
                }
              }
              return null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private List<ScalingInstance> findAllScalingInstances(
      String scalingGroupId, String activeScalingConfigurationId) throws ClientException {
    DescribeScalingInstancesRequest scalingInstancesRequest = new DescribeScalingInstancesRequest();
    scalingInstancesRequest.setScalingGroupId(scalingGroupId);
    scalingInstancesRequest.setScalingConfigurationId(activeScalingConfigurationId);
    int pageNumber = 1;
    int pageSize = 50;
    DescribeScalingInstancesResponse scalingInstancesResponse;
    List<ScalingInstance> scalingInstances = new ArrayList<>();
    while (true) {
      scalingInstancesRequest.setPageNumber(pageNumber);
      scalingInstancesRequest.setPageSize(pageSize);
      scalingInstancesResponse = client.getAcsResponse(scalingInstancesRequest);
      if (CollectionUtils.isEmpty(scalingInstancesResponse.getScalingInstances())) {
        break;
      } else {
        pageNumber = pageNumber + 1;
        for (ScalingInstance scalingInstance : scalingInstancesResponse.getScalingInstances()) {
          if (scalingInstance.getInstanceId() != null) {
            String instanceIds = "[\"" + scalingInstance.getInstanceId() + "\"]";
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.setInstanceIds(instanceIds);
            DescribeInstancesResponse acsResponse = client.getAcsResponse(request);
            if (acsResponse.getInstances() != null && acsResponse.getInstances().size() > 0) {
              Instance instance = acsResponse.getInstances().get(0);
              String zoneId = instance.getZoneId();
              scalingInstance.setCreationType(zoneId);
            }
          }
          scalingInstances.add(scalingInstance);
        }
        if (scalingInstancesResponse.getScalingInstances().size() < pageSize) {
          break;
        }
      }
    }
    return scalingInstances;
  }

  private CacheResult buildCacheResult(List<ClusterCachingBuilder> sgData) {

    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    resultMap.put(
        APPLICATIONS.ns,
        sgData.stream()
            .map(ClusterCachingBuilder::buildApplicationCacheData)
            .collect(Collectors.toMap(CacheData::getId, d -> d, CacheDataHelper::merge))
            .values());
    resultMap.put(
        CLUSTERS.ns,
        sgData.stream()
            .map(ClusterCachingBuilder::buildClusterCacheData)
            .collect(Collectors.toMap(CacheData::getId, d -> d, CacheDataHelper::merge))
            .values());
    resultMap.put(
        SERVER_GROUPS.ns,
        sgData.stream()
            .map(s -> s.buildServerGroupCacheData(objectMapper))
            .collect(Collectors.toMap(CacheData::getId, d -> d, CacheDataHelper::merge))
            .values());

    resultMap.put(
        LAUNCH_CONFIGS.ns,
        sgData.stream()
            .map(ClusterCachingBuilder::buildLaunchConfigCacheData)
            .collect(Collectors.toMap(CacheData::getId, d -> d, CacheDataHelper::merge))
            .values());

    return new DefaultCacheResult(resultMap);
  }

  public static class ClusterCachingBuilder {
    final ScalingGroup scalingGroup;
    final DescribeScalingConfigurationsResponse scalingConfigurationsResponse;
    final List<ScalingInstance> scalingInstances;
    final List<DescribeLoadBalancerAttributeResponse> loadBalancerAttributes;
    final Names name;
    final String applicationKey;
    final String clusterKey;
    final String serverGroupKey;
    final String launchConfigKey;
    final Set<String> loadBalancerKeys = new HashSet<>();
    final Set<String> instanceIds = new HashSet<>();
    final String securityGroupName;
    final String accountName;
    final String region;

    public ClusterCachingBuilder(
        ScalingGroup scalingGroup,
        String account,
        String region,
        DescribeScalingConfigurationsResponse scalingConfigurationsResponse,
        List<DescribeLoadBalancerAttributeResponse> loadBalancerAttributes,
        List<ScalingInstance> scalingInstances,
        String securityGroupName) {
      this.scalingGroup = scalingGroup;
      this.scalingConfigurationsResponse = scalingConfigurationsResponse;
      this.scalingInstances = scalingInstances;
      this.loadBalancerAttributes = loadBalancerAttributes;
      this.region = region;
      this.accountName = account;

      name = Names.parseName(scalingGroup.getScalingGroupName());
      applicationKey = Keys.getApplicationKey(name.getApp());
      clusterKey = Keys.getClusterKey(name.getCluster(), name.getApp(), account);
      serverGroupKey = Keys.getServerGroupKey(scalingGroup.getScalingGroupName(), account, region);
      launchConfigKey =
          Keys.getLaunchConfigKey(scalingGroup.getScalingGroupName(), account, region);
      for (DescribeLoadBalancerAttributeResponse loadBalancerAttribute : loadBalancerAttributes) {
        loadBalancerKeys.add(
            Keys.getLoadBalancerKey(
                loadBalancerAttribute.getLoadBalancerName(),
                account,
                region,
                loadBalancerAttribute.getVpcId()));
      }
      for (ScalingInstance scalingInstance : scalingInstances) {
        instanceIds.add(Keys.getInstanceKey(scalingInstance.getInstanceId(), account, region));
      }
      this.securityGroupName = securityGroupName;
    }

    public CacheData buildApplicationCacheData() {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("name", this.name.getApp());

      Map<String, Collection<String>> relationships = new HashMap<>(16);
      relationships.put(CLUSTERS.ns, Sets.ofModifiable(this.clusterKey));
      relationships.put(SERVER_GROUPS.ns, Sets.ofModifiable(this.serverGroupKey));
      relationships.put(LOAD_BALANCERS.ns, this.loadBalancerKeys);

      return new DefaultCacheData(applicationKey, attributes, relationships);
    }

    public CacheData buildClusterCacheData() {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("name", this.name.getCluster());
      attributes.put("application", this.name.getApp());
      attributes.put("accountName", this.accountName);

      Map<String, Collection<String>> relationships = new HashMap<>();
      relationships.put(APPLICATIONS.ns, Sets.ofModifiable(this.applicationKey));
      relationships.put(SERVER_GROUPS.ns, Sets.ofModifiable(this.serverGroupKey));
      relationships.put(LOAD_BALANCERS.ns, this.loadBalancerKeys);

      return new DefaultCacheData(clusterKey, attributes, relationships);
    }

    public CacheData buildServerGroupCacheData(ObjectMapper objectMapper) {
      Map<String, Object> attributes = new HashMap<>(16);
      attributes.put("application", this.name.getApp());
      attributes.put("scalingGroup", this.scalingGroup);
      attributes.put("region", this.region);
      attributes.put("name", this.scalingGroup.getScalingGroupName());
      if (this.scalingConfigurationsResponse.getScalingConfigurations().size() > 0) {
        attributes.put(
            "launchConfigName",
            this.scalingConfigurationsResponse
                .getScalingConfigurations()
                .get(0)
                .getScalingConfigurationName());
        ScalingConfiguration scalingConfiguration =
            this.scalingConfigurationsResponse.getScalingConfigurations().get(0);
        Map<String, Object> map = objectMapper.convertValue(scalingConfiguration, Map.class);
        map.put("securityGroupName", this.securityGroupName);
        attributes.put("scalingConfiguration", map);
      } else {
        attributes.put("scalingConfiguration", new ScalingConfiguration());
      }
      attributes.put("instances", this.scalingInstances);
      attributes.put("loadBalancers", this.loadBalancerAttributes);
      attributes.put("provider", AliCloudProvider.ID);
      attributes.put("account", this.accountName);
      attributes.put("regionId", this.region);

      Map<String, Collection<String>> relationships = new HashMap<>();
      relationships.put(APPLICATIONS.ns, Sets.ofModifiable(this.applicationKey));
      relationships.put(CLUSTERS.ns, Sets.ofModifiable(this.clusterKey));
      relationships.put(LOAD_BALANCERS.ns, this.loadBalancerKeys);
      relationships.put(LAUNCH_CONFIGS.ns, Sets.ofModifiable(this.launchConfigKey));
      relationships.put(INSTANCES.ns, this.instanceIds);
      return new DefaultCacheData(serverGroupKey, attributes, relationships);
    }

    public CacheData buildLaunchConfigCacheData() {
      Map<String, Object> attributes = new HashMap<>(16);
      Map<String, Collection<String>> relationships = new HashMap<>(16);
      relationships.put(SERVER_GROUPS.ns, Sets.ofModifiable(this.serverGroupKey));
      return new DefaultCacheData(launchConfigKey, attributes, relationships);
    }
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    return null;
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
  public String getAccountName() {
    return account.getName();
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

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }
}
