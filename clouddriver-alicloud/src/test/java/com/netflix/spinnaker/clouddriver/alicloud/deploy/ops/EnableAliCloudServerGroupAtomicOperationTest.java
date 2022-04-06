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
package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.ess.model.v20140828.EnableScalingGroupResponse;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.EnableAliCloudServerGroupDescription;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class EnableAliCloudServerGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeScalingGroupsAnswer())
        .thenAnswer(new DescribeScalingConfigurationsAnswer())
        .thenAnswer(new EnableScalingGroupAnswer());
  }

  @Test
  public void testOperate() {
    EnableAliCloudServerGroupAtomicOperation operation =
        new EnableAliCloudServerGroupAtomicOperation(buildDescription(), clientFactory);
    operation.operate(priorOutputs);
  }

  private EnableAliCloudServerGroupDescription buildDescription() {
    EnableAliCloudServerGroupDescription description = new EnableAliCloudServerGroupDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    return description;
  }

  private class DescribeScalingGroupsAnswer implements Answer<DescribeScalingGroupsResponse> {
    @Override
    public DescribeScalingGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeScalingGroupsResponse response = new DescribeScalingGroupsResponse();
      List<ScalingGroup> scalingGroups = new ArrayList<>();
      DescribeScalingGroupsResponse.ScalingGroup scalingGroup =
          new DescribeScalingGroupsResponse.ScalingGroup();
      scalingGroup.setScalingGroupId("test-ID");
      scalingGroup.setLifecycleState("Inactive");
      scalingGroups.add(scalingGroup);
      response.setScalingGroups(scalingGroups);
      return response;
    }
  }

  private class DescribeScalingConfigurationsAnswer
      implements Answer<DescribeScalingConfigurationsResponse> {
    @Override
    public DescribeScalingConfigurationsResponse answer(InvocationOnMock invocation)
        throws Throwable {
      DescribeScalingConfigurationsResponse response = new DescribeScalingConfigurationsResponse();
      List<ScalingConfiguration> scalingConfigurations = new ArrayList<>();
      ScalingConfiguration scalingConfiguration = new ScalingConfiguration();
      scalingConfiguration.setScalingConfigurationId("test-ScalingConfigurationId");
      scalingConfigurations.add(scalingConfiguration);
      response.setScalingConfigurations(scalingConfigurations);
      return response;
    }
  }

  private class EnableScalingGroupAnswer implements Answer<EnableScalingGroupResponse> {
    @Override
    public EnableScalingGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      EnableScalingGroupResponse response = new EnableScalingGroupResponse();
      return response;
    }
  }
}
