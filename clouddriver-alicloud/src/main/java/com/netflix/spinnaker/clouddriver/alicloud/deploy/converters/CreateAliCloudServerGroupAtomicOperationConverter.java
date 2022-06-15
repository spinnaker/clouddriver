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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.converters;

import com.aliyuncs.ess.model.v20140828.CreateScalingGroupRequest;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudOperation;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.BasicAliCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.ops.CreateAliCloudServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AliCloudOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("createAliCloudServerGroupDescription")
public class CreateAliCloudServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final ClientFactory clientFactory;

  private final List<ClusterProvider> clusterProviders;

  @Autowired
  public CreateAliCloudServerGroupAtomicOperationConverter(
      ClientFactory clientFactory, List<ClusterProvider> clusterProviders) {
    this.clientFactory = clientFactory;
    this.clusterProviders = clusterProviders;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new CreateAliCloudServerGroupAtomicOperation(
        convertDescription(input), getObjectMapper(), clientFactory, clusterProviders);
  }

  @Override
  public BasicAliCloudDeployDescription convertDescription(Map input) {
    BasicAliCloudDeployDescription description =
        getObjectMapper().convertValue(input, BasicAliCloudDeployDescription.class);

    ArrayList<Map> vServerGroups = (ArrayList<Map>) input.get("vServerGroups");
    if (vServerGroups != null) {
      List<CreateScalingGroupRequest.VServerGroup> vServerGroupsNew = new ArrayList<>();
      for (Map map : vServerGroups) {
        CreateScalingGroupRequest.VServerGroup vServerGroup =
            getObjectMapper().convertValue(map, CreateScalingGroupRequest.VServerGroup.class);
        ArrayList<Map> vServerGroupAttributes =
            (ArrayList<Map>)
                map.getOrDefault("vserverGroupAttributes", map.get("vServerGroupAttributes"));
        List<CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute>
            vServerGroupAttributesList = new ArrayList<>();

        if (vServerGroupAttributes != null) {
          for (Map vServerGroupAttributeMap : vServerGroupAttributes) {
            CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute vServerGroupAttribute =
                getObjectMapper()
                    .convertValue(
                        vServerGroupAttributeMap,
                        CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute.class);
            Object vServerGroupId =
                vServerGroupAttributeMap.getOrDefault(
                    "vserverGroupId", vServerGroupAttributeMap.get("vServerGroupId"));
            if (vServerGroupId != null) {
              vServerGroupAttribute.setVServerGroupId(vServerGroupId.toString());
            }
            vServerGroupAttributesList.add(vServerGroupAttribute);
          }
        }

        if (vServerGroupAttributesList.stream()
            .noneMatch(v -> StringUtils.isBlank(v.getVServerGroupId()))) {
          vServerGroup.setVServerGroupAttributes(vServerGroupAttributesList);
          vServerGroupsNew.add(vServerGroup);
        }
      }
      description.setVServerGroups(vServerGroupsNew);
    }
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
