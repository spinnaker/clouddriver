package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentNetworkDescription {
  private String vpcId;
  private String vpcName;
  private String cidrBlock;
  private Boolean isDefault;
}
