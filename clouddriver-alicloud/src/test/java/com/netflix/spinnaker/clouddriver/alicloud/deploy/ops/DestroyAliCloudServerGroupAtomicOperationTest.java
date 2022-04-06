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

import com.aliyuncs.ess.model.v20140828.DeleteScalingGroupResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DestroyAliCloudServerGroupDescription;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DestroyAliCloudServerGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new DescribeScalingGroupsAnswer())
        .thenAnswer(new DeleteScalingGroupAnswer());
  }

  @Test
  public void testOperate() {
    DestroyAliCloudServerGroupAtomicOperation operation =
        new DestroyAliCloudServerGroupAtomicOperation(buildDescription(), clientFactory);
    operation.operate(priorOutputs);
  }

  private DestroyAliCloudServerGroupDescription buildDescription() {
    DestroyAliCloudServerGroupDescription description = new DestroyAliCloudServerGroupDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    description.setServerGroupName("test-ServerGroupName");
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
      scalingGroup.setLifecycleState("Active");
      scalingGroups.add(scalingGroup);
      response.setScalingGroups(scalingGroups);
      return response;
    }
  }

  private class DeleteScalingGroupAnswer implements Answer<DeleteScalingGroupResponse> {
    @Override
    public DeleteScalingGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      DeleteScalingGroupResponse response = new DeleteScalingGroupResponse();
      return response;
    }
  }
}
