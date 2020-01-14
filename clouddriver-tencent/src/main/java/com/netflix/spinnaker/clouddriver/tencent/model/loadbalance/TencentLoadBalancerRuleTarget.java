package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TencentLoadBalancerRuleTarget {
  private String locationId;
  private String domain;
  private String url;
  private List<TencentLoadBalancerTarget> targets;
}
