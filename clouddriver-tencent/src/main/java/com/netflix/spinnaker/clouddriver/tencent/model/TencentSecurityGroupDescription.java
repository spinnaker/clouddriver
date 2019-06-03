package com.netflix.spinnaker.clouddriver.tencent.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentSecurityGroupDescription {
  private String securityGroupId;
  private String securityGroupName;
  private String securityGroupDesc;
  private List<TencentSecurityGroupRule> inRules;
  private List<TencentSecurityGroupRule> outRules;
  private long lastReadTime;
}
