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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.ess.model.v20140828.ModifyScalingConfigurationRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpdateAliCloudLaunchConfigDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateAliCloudLaunchConfigAtomicOperation implements AtomicOperation<Void> {

  private final UpdateAliCloudLaunchConfigDescription description;

  private final ObjectMapper objectMapper;

  private final ClientFactory clientFactory;

  public UpdateAliCloudLaunchConfigAtomicOperation(
      UpdateAliCloudLaunchConfigDescription description,
      ObjectMapper objectMapper,
      ClientFactory clientFactory) {
    this.description = description;
    this.objectMapper = objectMapper;
    this.clientFactory = clientFactory;
  }

  @Override
  public Void operate(List priorOutputs) {
    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(), description.getCredentials().getCredentialsProvider());
    DescribeScalingGroupsRequest describeScalingGroupsRequest = new DescribeScalingGroupsRequest();
    describeScalingGroupsRequest.setScalingGroupName(description.getServerGroupName());
    describeScalingGroupsRequest.setPageSize(50);
    DescribeScalingGroupsResponse describeScalingGroupsResponse;
    try {
      describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
      for (ScalingGroup scalingGroup : describeScalingGroupsResponse.getScalingGroups()) {
        String activeScalingConfigurationId = scalingGroup.getActiveScalingConfigurationId();
        ModifyScalingConfigurationRequest configurationRequest =
            objectMapper.convertValue(description, ModifyScalingConfigurationRequest.class);
        configurationRequest.setScalingConfigurationId(activeScalingConfigurationId);
        if (description.getSecurityGroups() != null) {
          for (String securityGroupId : description.getSecurityGroups()) {
            configurationRequest.setSecurityGroupId(securityGroupId);
          }
        }
        client.getAcsResponse(configurationRequest);
      }
    } catch (ServerException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    return null;
  }
}
