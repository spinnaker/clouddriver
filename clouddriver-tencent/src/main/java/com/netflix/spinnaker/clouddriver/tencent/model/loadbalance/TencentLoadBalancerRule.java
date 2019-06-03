package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TencentLoadBalancerRule {
  private String locationId;
  private String domain;
  private String url;
  private Integer sessionExpireTime;
  private TencentLoadBalancerHealthCheck healthCheck;
  private TencentLoadBalancerCertificate certificate;
  private String scheduler;
  private List<TencentLoadBalancerTarget> targets;
}
