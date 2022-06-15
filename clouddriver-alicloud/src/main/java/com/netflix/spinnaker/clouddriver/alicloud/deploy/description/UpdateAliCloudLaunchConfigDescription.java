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

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateAliCloudLaunchConfigDescription extends BaseAliCloudDescription {

  private String imageId;

  private Integer memory;

  private String ioOptimized;

  private List<String> instanceTypes;

  private Integer internetMaxBandwidthOut;

  private String securityGroupId;

  private String keyPairName;

  private List<SpotPriceLimit> spotPriceLimits;

  private String systemDiskCategory;

  private String userData;

  private String resourceGroupId;

  private String hostName;

  private Boolean passwordInherit;

  private String imageName;

  private Boolean override;

  private String deploymentSetId;

  private String resourceOwnerAccount;

  private String ownerAccount;

  private Integer cpu;

  private String systemDiskDiskName;

  private String ramRoleName;

  private Long ownerId;

  private List<DataDisk> dataDisks;

  private String scalingConfigurationName;

  private String tags;

  private String scalingConfigurationId;

  private String spotStrategy;

  private String instanceName;

  private Integer loadBalancerWeight;

  private Integer systemDiskSize;

  private String internetChargeType;

  private String systemDiskDescription;

  private String scalingGroupName;

  private String serverGroupName;

  private List<String> securityGroups;

  @Data
  public static class SpotPriceLimit {

    private String instanceType;

    private Float priceLimit;
  }

  @Data
  public static class DataDisk {

    private String diskName;

    private String snapshotId;

    private Integer size;

    private String encrypted;

    private String description;

    private String category;

    private String kMSKeyId;

    private String device;

    private Boolean deleteWithInstance;
  }
}
