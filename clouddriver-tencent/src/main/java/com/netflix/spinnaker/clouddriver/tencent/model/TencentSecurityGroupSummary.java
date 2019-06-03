package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode()
@AllArgsConstructor
public class TencentSecurityGroupSummary implements SecurityGroupSummary {
  private String name;
  @EqualsAndHashCode.Include private String id;
}
