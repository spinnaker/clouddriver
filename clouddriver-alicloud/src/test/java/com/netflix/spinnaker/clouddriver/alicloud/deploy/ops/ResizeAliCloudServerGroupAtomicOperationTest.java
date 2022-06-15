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
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.ess.model.v20140828.ModifyScalingGroupResponse;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.ResizeAliCloudServerGroupDescription;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ResizeAliCloudServerGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeScalingGroupsAnswer())
        .thenAnswer(new ModifyScalingGroupAnswer());
  }

  @Test
  public void testOperate() {
    ResizeAliCloudServerGroupAtomicOperation operation =
        new ResizeAliCloudServerGroupAtomicOperation(buildDescription(), clientFactory);
    operation.operate(priorOutputs);
  }

  private ResizeAliCloudServerGroupDescription buildDescription() {
    ResizeAliCloudServerGroupDescription description = new ResizeAliCloudServerGroupDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    description.setMaxSize(10);
    description.setMinSize(1);
    LinkedHashMap<String, Integer> capacity = new LinkedHashMap<>();
    capacity.put("max", 3);
    capacity.put("min", 1);
    description.setCapacity(capacity);
    return description;
  }

  private class DescribeScalingGroupsAnswer implements Answer<DescribeScalingGroupsResponse> {
    @Override
    public DescribeScalingGroupsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeScalingGroupsResponse response = new DescribeScalingGroupsResponse();
      List<ScalingGroup> scalingGroups = new ArrayList<>();
      ScalingGroup scalingGroup = new ScalingGroup();
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
