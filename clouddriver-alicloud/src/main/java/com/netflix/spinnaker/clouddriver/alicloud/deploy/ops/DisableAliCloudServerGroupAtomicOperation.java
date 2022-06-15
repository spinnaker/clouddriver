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
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingInstancesResponse.ScalingInstance;
import com.aliyuncs.ess.model.v20140828.DisableScalingGroupRequest;
import com.aliyuncs.ess.model.v20140828.RemoveInstancesRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DisableAliCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisableAliCloudServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final DisableAliCloudServerGroupDescription description;

  private final ClientFactory clientFactory;

  public DisableAliCloudServerGroupAtomicOperation(
      DisableAliCloudServerGroupDescription description, ClientFactory clientFactory) {
    this.description = description;
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
        if ("Active".equals(scalingGroup.getLifecycleState())) {
          Integer maxSize = scalingGroup.getMaxSize();
          Integer minSize = scalingGroup.getMinSize();
          if (maxSize != null && maxSize == 0 && minSize != null && minSize == 0) {
            // Number of query instances
            DescribeScalingInstancesRequest scalingInstancesRequest =
                new DescribeScalingInstancesRequest();
            scalingInstancesRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
            scalingInstancesRequest.setScalingConfigurationId(
                scalingGroup.getActiveScalingConfigurationId());
            scalingInstancesRequest.setPageSize(50);
            DescribeScalingInstancesResponse scalingInstancesResponse =
                client.getAcsResponse(scalingInstancesRequest);
            List<ScalingInstance> scalingInstances = scalingInstancesResponse.getScalingInstances();
            if (scalingInstances != null && scalingInstances.size() > 0) {
              // Remove instance
              List<String> instanceIds = new ArrayList<>();
              scalingInstances.forEach(
                  scalingInstance -> {
                    instanceIds.add(scalingInstance.getInstanceId());
                  });
              RemoveInstancesRequest removeInstancesRequest = new RemoveInstancesRequest();
              removeInstancesRequest.setInstanceIds(instanceIds);
              removeInstancesRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
              client.getAcsResponse(removeInstancesRequest);
            }
          }

          DisableScalingGroupRequest disableScalingGroupRequest = new DisableScalingGroupRequest();
          disableScalingGroupRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
          client.getAcsResponse(disableScalingGroupRequest);
        }
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
