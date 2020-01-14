package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode()
class TencentSecurityGroupSummary implements SecurityGroupSummary {
  private String name;
  @EqualsAndHashCode.Include
  private String id;
}
