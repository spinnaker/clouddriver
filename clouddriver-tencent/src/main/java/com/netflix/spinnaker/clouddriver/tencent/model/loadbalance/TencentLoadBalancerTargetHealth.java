package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentLoadBalancerTargetHealth {
  private String instanceId;
  private Integer port;
  private Boolean healthStatus;
  private String loadBalancerId;
  private String listenerId;
  private String locationId;
}
