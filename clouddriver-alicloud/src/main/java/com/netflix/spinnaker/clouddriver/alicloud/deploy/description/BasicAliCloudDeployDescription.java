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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.description;

import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationRequest;
import com.aliyuncs.ess.model.v20140828.CreateScalingGroupRequest.LifecycleHook;
import com.aliyuncs.ess.model.v20140828.CreateScalingGroupRequest.VServerGroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment.Capacity;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BasicAliCloudDeployDescription extends BaseAliCloudDescription
    implements DeployDescription, ApplicationNameable {

  private String multiAZPolicy;
  private String dBInstanceIds;
  private String launchTemplateId;
  private String loadBalancerIds;
  private String healthCheckType;
  private String resourceOwnerAccount;
  private String scalingGroupName;
  private String clientToken;
  private String ownerAccount;
  private Integer minSize;
  private Long ownerId;
  private String launchTemplateVersion;
  private String scalingPolicy;
  private Integer maxSize;
  private List<LifecycleHook> lifecycleHooks;
  private Integer defaultCooldown;
  private String removalPolicy1;
  private List<VServerGroup> vServerGroups;
  private String removalPolicy2;
  private String scalingGroupId;
  private String application;
  private String stack;
  private String freeFormDetails;
  private List<CreateScalingConfigurationRequest> scalingConfigurations;

  @JsonProperty("vSwitchId")
  private String vSwitchId;

  @JsonProperty("vSwitchIds")
  private List<String> vSwitchIds;

  Capacity capacity = new Capacity();
  Source source = new Source();

  @Override
  public Collection<String> getApplications() {
    List<String> applications = new ArrayList<>();
    applications.add(application);
    return applications;
  }

  @Data
  public static class Source {
    private String account;
    private String region;
    private String asgName;
    private Boolean useSourceCapacity;
  }
}
