package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical

@Canonical
class TencentLoadBalancerHealthCheck {
  Integer healthSwitch;
  Integer timeOut;
  Integer intervalTime;
  Integer healthNum;
  Integer unHealthNum;
  Integer httpCode;
  String httpCheckPath;
  String httpCheckDomain;
  String httpCheckMethod;

  void copyHealthCheck(TencentLoadBalancerHealthCheck health) {
    if (health != null) {
      this.healthSwitch = health.healthSwitch
      this.timeOut = health.timeOut
      this.intervalTime = health.intervalTime
      this.healthNum = health.healthNum
      this.unHealthNum = health.unHealthNum
      this.httpCode = health.httpCode
      this.httpCheckPath = health.httpCheckPath
      this.httpCheckDomain = health.httpCheckDomain
      this.httpCheckMethod = health.httpCheckMethod
    }
  }
}
