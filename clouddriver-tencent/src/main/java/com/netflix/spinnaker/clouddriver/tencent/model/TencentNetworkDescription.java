package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentNetworkDescription {
  private String vpcId;
  private String vpcName;
  private String cidrBlock;
  private Boolean isDefault;
}
