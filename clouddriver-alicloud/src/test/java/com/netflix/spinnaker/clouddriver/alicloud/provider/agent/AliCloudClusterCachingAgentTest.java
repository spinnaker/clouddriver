/*
 * Copyright 2022 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.AcsResponse;
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
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse.ScalingInstance;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudClusterCachingAgentTest extends CommonCachingAgentTest {

  private final String SCALINGGROUPID = "sgid";
  private final String SCALINGGROUPNAME = "spinnaker-ess-test";
  private final String SCALINGCONFIGID = "scid";
  private final String LOADBALANCERID = "lbid";
  private final String LOADBALANCERNAME = "lbname";
  private final String INSTANCEID = "inid";
  private final String INSTANCEID2 = "inid2";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any())).thenAnswer(new CommonAnswer());
  }

  @Test
  public void testLoadData() {

    Names name = Names.parseName(SCALINGGROUPNAME);
    AliCloudClusterCachingAgent agent =
        new AliCloudClusterCachingAgent(account, REGION, objectMapper, client);

    CacheResult result = agent.loadData(providerCache);

    Map<String, Collection<CacheData>> cacheResults = result.getCacheResults();
    Collection<CacheData> applications = cacheResults.get(APPLICATIONS.ns);
    Collection<CacheData> clusters = cacheResults.get(CLUSTERS.ns);
    Collection<CacheData> serverGroups = cacheResults.get(SERVER_GROUPS.ns);
    Collection<CacheData> launchConfigs = cacheResults.get(LAUNCH_CONFIGS.ns);

    String applicationKey = Keys.getApplicationKey(name.getApp());
    String clusterKey = Keys.getClusterKey(name.getCluster(), name.getApp(), ACCOUNT);
    String serverGroupKey = Keys.getServerGroupKey(SCALINGGROUPNAME, ACCOUNT, REGION);
    String launchConfigKey = Keys.getLaunchConfigKey(SCALINGGROUPNAME, ACCOUNT, REGION);

    assertEquals(1, applications.size());
    assertEquals(applicationKey, applications.iterator().next().getId());

    assertEquals(1, clusters.size());
    assertEquals(clusterKey, clusters.iterator().next().getId());

    assertEquals(1, serverGroups.size());
    assertEquals(serverGroupKey, serverGroups.iterator().next().getId());

    assertEquals(1, launchConfigs.size());
    assertEquals(launchConfigKey, launchConfigs.iterator().next().getId());
  }

  private class CommonAnswer implements Answer<AcsResponse> {
    @Override
    public AcsResponse answer(InvocationOnMock invocation) throws Throwable {
      Object argument = invocation.getArgument(0);
      if (argument instanceof DescribeScalingGroupsRequest) {
        DescribeScalingGroupsResponse response = new DescribeScalingGroupsResponse();
        List<ScalingGroup> scalingGroups = new ArrayList<>();
        ScalingGroup scalingGroup = new ScalingGroup();
        scalingGroup.setMinSize(3);
        scalingGroup.setMaxSize(10);
        scalingGroup.setScalingGroupId(SCALINGGROUPID);
        scalingGroup.setActiveScalingConfigurationId(SCALINGCONFIGID);
        scalingGroup.setScalingGroupName(SCALINGGROUPNAME);

        List<String> loadBalancerIds = new ArrayList<>();
        loadBalancerIds.add(LOADBALANCERID);
        scalingGroup.setLoadBalancerIds(loadBalancerIds);

        scalingGroups.add(scalingGroup);
        response.setScalingGroups(scalingGroups);
        return response;
      } else if (argument instanceof DescribeScalingConfigurationsRequest) {
        DescribeScalingConfigurationsResponse response =
            new DescribeScalingConfigurationsResponse();
        List<ScalingConfiguration> scalingConfigurations = new ArrayList<>();
        ScalingConfiguration scalingConfiguration = new ScalingConfiguration();
        scalingConfiguration.setScalingConfigurationId(SCALINGCONFIGID);

        scalingConfigurations.add(scalingConfiguration);
        response.setScalingConfigurations(scalingConfigurations);
        return response;
      } else if (argument instanceof DescribeLoadBalancerAttributeRequest) {
        DescribeLoadBalancerAttributeResponse response =
            new DescribeLoadBalancerAttributeResponse();
        response.setLoadBalancerId(LOADBALANCERID);
        response.setLoadBalancerName(LOADBALANCERNAME);
        return response;
      } else if (argument instanceof DescribeScalingInstancesRequest) {
        DescribeScalingInstancesResponse response = new DescribeScalingInstancesResponse();
        List<ScalingInstance> scalingInstances = new ArrayList<>();
        ScalingInstance instance = new ScalingInstance();
        instance.setInstanceId(INSTANCEID);

        scalingInstances.add(instance);

        ScalingInstance instance2 = new ScalingInstance();
        instance2.setInstanceId(INSTANCEID2);
        scalingInstances.add(instance2);

        response.setScalingInstances(scalingInstances);
        return response;
      } else if (argument instanceof DescribeSecurityGroupsRequest) {
        DescribeSecurityGroupsResponse response = new DescribeSecurityGroupsResponse();
        List<SecurityGroup> securityGroups = new ArrayList<>();
        SecurityGroup securityGroup = new SecurityGroup();
        securityGroup.setSecurityGroupName("test-SecurityGroupName");
        securityGroups.add(securityGroup);
        response.setSecurityGroups(securityGroups);
        return response;
      } else if (argument instanceof DescribeInstancesRequest) {
        DescribeInstancesResponse response = new DescribeInstancesResponse();
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance();
        instance.setInstanceName("test-InstanceName");
        instances.add(instance);
        response.setInstances(instances);
        return response;
      }
      return null;
    }
  }
}
