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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ess.model.v20140828.DeleteScalingGroupRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DestroyAliCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class DestroyAliCloudServerGroupAtomicOperation implements AtomicOperation<Void> {


  private final DestroyAliCloudServerGroupDescription description;

  private final ClientFactory clientFactory;

  public DestroyAliCloudServerGroupAtomicOperation(
      DestroyAliCloudServerGroupDescription description, ClientFactory clientFactory) {
    this.description = description;
    this.clientFactory = clientFactory;
  }

  @Override
  public Void operate(List priorOutputs) {
    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(),
            description.getCredentials().getAccessKeyId(),
            description.getCredentials().getAccessSecretKey());

    DescribeScalingGroupsRequest describeScalingGroupsRequest = new DescribeScalingGroupsRequest();
    if (StringUtils.isNotEmpty(description.getServerGroupName())) {
      describeScalingGroupsRequest.setScalingGroupName(description.getServerGroupName());
    } else if (StringUtils.isNotEmpty(description.getScalingGroupName())) {
      describeScalingGroupsRequest.setScalingGroupName(description.getScalingGroupName());
    } else {
      throw new AliCloudException("Not passed Server Group");
    }

    describeScalingGroupsRequest.setPageSize(50);
    DescribeScalingGroupsResponse describeScalingGroupsResponse;
    try {
      describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
      for (ScalingGroup scalingGroup : describeScalingGroupsResponse.getScalingGroups()) {
        DeleteScalingGroupRequest deleteScalingGroupRequest = new DeleteScalingGroupRequest();
        deleteScalingGroupRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
        deleteScalingGroupRequest.setForceDelete(true);
        client.getAcsResponse(deleteScalingGroupRequest);
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
