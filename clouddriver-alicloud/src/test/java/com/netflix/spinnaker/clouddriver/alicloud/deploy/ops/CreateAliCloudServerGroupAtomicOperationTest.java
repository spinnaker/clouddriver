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

import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationRequest;
import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationResponse;
import com.aliyuncs.ess.model.v20140828.CreateScalingGroupResponse;
import com.aliyuncs.ess.model.v20140828.EnableScalingGroupResponse;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.BasicAliCloudDeployDescription;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class CreateAliCloudServerGroupAtomicOperationTest extends CommonAtomicOperation {

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new CreateScalingGroupAnswer())
        .thenAnswer(new CreateScalingConfigurationAnswer())
        .thenAnswer(new EnableScalingGroupAnswer());
  }

  @Test
  public void testOperate() {
    CreateAliCloudServerGroupAtomicOperation operation =
        new CreateAliCloudServerGroupAtomicOperation(
            buildDescription(), objectMapper, clientFactory, clusterProviders);
    operation.operate(priorOutputs);
  }

  private BasicAliCloudDeployDescription buildDescription() {
    BasicAliCloudDeployDescription description = new BasicAliCloudDeployDescription();
    description.setRegion(REGION);
    description.setCredentials(credentials);
    description.setScalingGroupName("test-Name");
    List<CreateScalingConfigurationRequest> scalingConfigurations = new ArrayList<>();
    CreateScalingConfigurationRequest request = new CreateScalingConfigurationRequest();
    request.setScalingConfigurationName("test-ScalingConfigurationName");
    scalingConfigurations.add(request);
    description.setScalingConfigurations(scalingConfigurations);
    description.setApplication("spin63");
    description.setStack("test");
    description.setFreeFormDetails("test");
    return description;
  }

  private class CreateScalingGroupAnswer implements Answer<CreateScalingGroupResponse> {
    @Override
    public CreateScalingGroupResponse answer(InvocationOnMock invocation) throws Throwable {
      CreateScalingGroupResponse response = new CreateScalingGroupResponse();
      response.setScalingGroupId("test-ScalingGroupId");
      return response;
    }
  }

  private class CreateScalingConfigurationAnswer
      implements Answer<CreateScalingConfigurationResponse> {
    @Override
    public CreateScalingConfigurationResponse answer(InvocationOnMock invocation) throws Throwable {
      CreateScalingConfigurationResponse response = new CreateScalingConfigurationResponse();
      response.setScalingConfigurationId("test-ScalingConfigurationId");
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
