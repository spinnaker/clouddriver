package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentLoadBalancerTarget {
  private String instanceId;
  private Integer port;
  private String type;
  private Integer weight;
}
