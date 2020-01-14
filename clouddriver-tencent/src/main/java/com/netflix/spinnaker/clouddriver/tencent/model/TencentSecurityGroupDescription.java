package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TencentSecurityGroupDescription {
  private String securityGroupId;
  private String securityGroupName;
  private String securityGroupDesc;
  private List<TencentSecurityGroupRule> inRules;
  private List<TencentSecurityGroupRule> outRules;
  private long lastReadTime;
}
