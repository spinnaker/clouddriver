package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentLoadBalancerTarget {
  private String instanceId;
  private Integer port;
  private String type;
  private Integer weight;
}
