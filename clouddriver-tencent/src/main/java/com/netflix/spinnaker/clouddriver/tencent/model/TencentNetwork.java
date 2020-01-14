package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Network;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentNetwork implements Network {
  private String cloudProvider = TencentCloudProvider.ID;
  private String id;
  private String name;
  private String account;
  private String region;
  private String cidrBlock;
  private Boolean isDefault;
}
