package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentSubnetDescription {
  private String subnetId;
  private String vpcId;
  private String subnetName;
  private String cidrBlock;
  private Boolean isDefault;
  private String zone;
}
