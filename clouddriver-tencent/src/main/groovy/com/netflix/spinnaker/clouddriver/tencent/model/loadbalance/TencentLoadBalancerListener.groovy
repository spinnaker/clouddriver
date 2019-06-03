package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical


@Canonical
class TencentLoadBalancerListener {
  String listenerId
  String listenerName
  String protocol
  Integer port
  TencentLoadBalancerHealthCheck healthCheck
  TencentLoadBalancerCertificate certificate
  Integer sessionExpireTime
  String scheduler
  Integer sniSwitch

  //target, tcp/udp 4 layer
  List<TencentLoadBalancerTarget> targets

  //rule, http/https 7 layer
  List<TencentLoadBalancerRule> rules

  void copyListener(TencentLoadBalancerListener listener) {
    if (listener == null) {
      return
    }
    this.listenerId = listener.listenerId
    this.listenerName = listener.listenerName
    this.protocol = listener.protocol
    this.port = listener.port
    this.sessionExpireTime = listener.sessionExpireTime
    this.scheduler = listener.scheduler
    this.sniSwitch = listener.sniSwitch
    this.healthCheck = new TencentLoadBalancerHealthCheck()
    this.healthCheck.copyHealthCheck(listener.healthCheck)
    this.certificate = new TencentLoadBalancerCertificate()
    this.certificate.copyCertificate(listener.certificate)
    this.targets = listener.targets.collect {
      def target = new TencentLoadBalancerTarget()
      target.instanceId = it.instanceId
      target.port = it.port
      target.weight = it.weight
      target.type = it.type
      target
    }
    this.rules = listener.rules.collect {
      def rule = new TencentLoadBalancerRule()
      rule.locationId = it.locationId
      rule.domain = it.domain
      rule.url = it.url
      rule.sessionExpireTime = it.sessionExpireTime
      rule.scheduler = it.scheduler
      rule.healthCheck = new TencentLoadBalancerHealthCheck()
      rule.healthCheck.copyHealthCheck(it.healthCheck)
      rule.certificate = new TencentLoadBalancerCertificate()
      rule.certificate.copyCertificate(it.certificate)
      rule.targets = it.targets.collect {
        def target = new TencentLoadBalancerTarget()
        target.instanceId = it.instanceId
        target.port = it.port
        target.weight = it.weight
        target.type = it.type
        target
      }
      rule
    }

  }
}
