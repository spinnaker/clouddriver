package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentLoadBalancerTargetHealth {
  private String instanceId;
  private Integer port;
  private Boolean healthStatus;
  private String loadBalancerId;
  private String listenerId;
  private String locationId;
}
