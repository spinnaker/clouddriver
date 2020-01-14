package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentLoadBalancerHealthCheck {
  private Integer healthSwitch;
  private Integer timeOut;
  private Integer intervalTime;
  private Integer healthNum;
  private Integer unHealthNum;
  private Integer httpCode;
  private String httpCheckPath;
  private String httpCheckDomain;
  private String httpCheckMethod;

  public void copyHealthCheck(TencentLoadBalancerHealthCheck health) {
    if (health != null) {
      this.healthSwitch = health.healthSwitch;
      this.timeOut = health.timeOut;
      this.intervalTime = health.intervalTime;
      this.healthNum = health.healthNum;
      this.unHealthNum = health.unHealthNum;
      this.httpCode = health.httpCode;
      this.httpCheckPath = health.httpCheckPath;
      this.httpCheckDomain = health.httpCheckDomain;
      this.httpCheckMethod = health.httpCheckMethod;
    }
  }
}
