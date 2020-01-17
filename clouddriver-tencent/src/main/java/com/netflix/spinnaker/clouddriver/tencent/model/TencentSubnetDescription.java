package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentSubnetDescription {
  private String subnetId;
  private String vpcId;
  private String subnetName;
  private String cidrBlock;
  private Boolean isDefault;
  private String zone;
}
