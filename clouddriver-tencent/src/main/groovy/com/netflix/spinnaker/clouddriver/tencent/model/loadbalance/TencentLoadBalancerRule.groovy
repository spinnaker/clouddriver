package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical

@Canonical
class TencentLoadBalancerRule {
  String locationId
  String domain;
  String url;
  Integer sessionExpireTime;
  TencentLoadBalancerHealthCheck healthCheck;
  TencentLoadBalancerCertificate certificate;
  String scheduler;
  List<TencentLoadBalancerTarget> targets
}
