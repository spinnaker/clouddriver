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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.description;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModifyScalingGroupDescription extends BaseAliCloudDescription {

  private String scalingGroupName;

  private String serverGroupName;

  private Long resourceOwnerId;

  private String healthCheckType;

  private String launchTemplateId;

  private String resourceOwnerAccount;

  private String scalingGroupId;

  @JsonProperty("vSwitchIds")
  private List<String> vSwitchIds;

  private String ownerAccount;

  private String activeScalingConfigurationId;

  private Integer minSize;

  private Long ownerId;

  private String launchTemplateVersion;

  private Integer maxSize;

  private Integer defaultCooldown;

  private String removalPolicy1;

  private String removalPolicy2;

  private List<ScalingGroup> scalingGroups;

  @Data
  public static class ScalingGroup {

    private String scalingGroupName;

    private String region;
  }
}
