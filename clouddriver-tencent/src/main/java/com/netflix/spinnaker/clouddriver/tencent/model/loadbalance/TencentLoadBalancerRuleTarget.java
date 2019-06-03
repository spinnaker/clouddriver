package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentLoadBalancerRuleTarget {
  private String locationId;
  private String domain;
  private String url;
  private List<TencentLoadBalancerTarget> targets;
}
