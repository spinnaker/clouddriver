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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.alicloud.model.Listener;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import java.util.List;
import lombok.Data;

@Data
public class UpsertAliCloudLoadBalancerDescription {

  @JsonIgnore private AliCloudCredentials credentials;

  private String region;

  private List<Listener> listeners;

  private String loadBalancerId;

  private Long resourceOwnerId;

  private String clientToken;

  private String addressIPVersion;

  private String masterZoneId;

  private Integer duration;

  private String resourceGroupId;

  private String loadBalancerName;

  private String addressType;

  private String slaveZoneId;

  private String loadBalancerSpec;

  private Boolean autoPay;

  private String address;

  private String resourceOwnerAccount;

  private Integer bandwidth;

  private String ownerAccount;

  private Long ownerId;

  @JsonProperty("vSwitchId")
  private String vSwitchId;

  private String internetChargeType;

  private String vpcId;

  private String payType;

  private String pricingCycle;

  private String deleteProtection;
}
