/*
 * Copyright 2022 Netflix, Inc.
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
import com.aliyuncs.ecs.model.v20140526.DeleteSecurityGroupRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DeleteAliCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteAliCloudSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final DeleteAliCloudSecurityGroupDescription description;

  private final ClientFactory clientFactory;

  public DeleteAliCloudSecurityGroupAtomicOperation(
      DeleteAliCloudSecurityGroupDescription description, ClientFactory clientFactory) {
    this.description = description;
    this.clientFactory = clientFactory;
  }

  @Override
  public Void operate(List priorOutputs) {
    for (String region : description.getRegions()) {
      IAcsClient client =
          clientFactory.createClient(
              region,
              description.getCredentials().getAccessKeyId(),
              description.getCredentials().getAccessSecretKey());
      DescribeSecurityGroupsRequest describeSecurityGroupsRequest =
          new DescribeSecurityGroupsRequest();
      describeSecurityGroupsRequest.setSecurityGroupName(description.getSecurityGroupName());
      describeSecurityGroupsRequest.setPageSize(50);
      DescribeSecurityGroupsResponse describeSecurityGroupsResponse;
      try {
        describeSecurityGroupsResponse = client.getAcsResponse(describeSecurityGroupsRequest);
        List<SecurityGroup> securityGroups = describeSecurityGroupsResponse.getSecurityGroups();
        for (SecurityGroup securityGroup : securityGroups) {
          DeleteSecurityGroupRequest request = new DeleteSecurityGroupRequest();
          request.setSecurityGroupId(securityGroup.getSecurityGroupId());
          client.getAcsResponse(request);
        }

      } catch (ServerException e) {
        log.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      } catch (ClientException e) {
        log.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      }
    }
    return null;
  }
}
