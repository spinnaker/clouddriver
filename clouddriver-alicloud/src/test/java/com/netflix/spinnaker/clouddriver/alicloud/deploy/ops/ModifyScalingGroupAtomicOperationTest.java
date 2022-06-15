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

import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.ModifyScalingGroupResponse;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.ModifyScalingGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.ModifyScalingGroupDescription.ScalingGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ModifyScalingGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeScalingGroupsAnswer())
        .thenAnswer(new ModifyScalingGroupAnswer());
  }

  @Test
  public void testOperate() {
    ModifyScalingGroupAtomicOperation operation =
        new ModifyScalingGroupAtomicOperation(buildDescription(), objectMapper, clientFactory);
    operation.operate(priorOutputs);
  }

  private ModifyScalingGroupDescription buildDescription() {
    ModifyScalingGroupDescription description = new ModifyScalingGroupDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    List<ScalingGroup> scalingGroups = new ArrayList<>();
    ScalingGroup scalingGroup = new ScalingGroup();
    scalingGroup.setScalingGroupName("test-Name");
    scalingGroup.setRegion(REGION);
    scalingGroups.add(scalingGroup);
    description.setScalingGroups(scalingGroups);
    return description;
  }

  private class DescribeScalingGroupsAnswer implements Answer<DescribeScalingGroupsResponse> {
    @Override
    public DescribeScalingGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeScalingGroupsResponse response = new DescribeScalingGroupsResponse();
      List<DescribeScalingGroupsResponse.ScalingGroup> scalingGroups = new ArrayList<>();
      DescribeScalingGroupsResponse.ScalingGroup scalingGroup =
          new DescribeScalingGroupsResponse.ScalingGroup();
      scalingGroup.setScalingGroupId("test-ID");
      scalingGroups.add(scalingGroup);
      response.setScalingGroups(scalingGroups);
      return response;
    }
  }

  private class ModifyScalingGroupAnswer implements Answer<ModifyScalingGroupResponse> {
    @Override
    public ModifyScalingGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      ModifyScalingGroupResponse response = new ModifyScalingGroupResponse();
      return response;
    }
  }
}
