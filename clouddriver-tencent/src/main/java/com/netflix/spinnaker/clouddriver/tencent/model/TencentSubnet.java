package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Subnet;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentSubnet implements Subnet {
  private final String type = TencentCloudProvider.ID;
  private String name;
  private String id;
  private String account;
  private String region;
  private String vpcId;
  private String cidrBlock;
  private Boolean isDefault;
  private String zone;
  private String purpose;
}
