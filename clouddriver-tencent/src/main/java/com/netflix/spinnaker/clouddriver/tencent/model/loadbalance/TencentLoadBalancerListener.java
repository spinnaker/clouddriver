package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class TencentLoadBalancerListener {
  private String listenerId;
  private String listenerName;
  private String protocol;
  private Integer port;
  private TencentLoadBalancerHealthCheck healthCheck;
  private TencentLoadBalancerCertificate certificate;
  private Integer sessionExpireTime;
  private String scheduler;
  private Integer sniSwitch;

  //target, tcp/udp 4 layer
  private List<TencentLoadBalancerTarget> targets;

  //rule, http/https 7 layer;
  private List<TencentLoadBalancerRule> rules;

  public void copyListener(TencentLoadBalancerListener listener) {
    if (listener == null) {
      return;
    }
    this.listenerId = listener.listenerId;
    this.listenerName = listener.listenerName;
    this.protocol = listener.protocol;
    this.port = listener.port;
    this.sessionExpireTime = listener.sessionExpireTime;
    this.scheduler = listener.scheduler;
    this.sniSwitch = listener.sniSwitch;
    this.healthCheck = TencentLoadBalancerHealthCheck.builder().build();
    this.healthCheck.copyHealthCheck(listener.healthCheck);
    this.certificate = TencentLoadBalancerCertificate.builder().build();
    this.certificate.copyCertificate(listener.certificate);
    this.targets = listener.targets.stream().map(it -> {
      TencentLoadBalancerTarget.TencentLoadBalancerTargetBuilder targetBuilder = TencentLoadBalancerTarget.builder();
      targetBuilder.instanceId(it.getInstanceId());
      targetBuilder.port(it.getPort());
      targetBuilder.weight(it.getWeight());
      targetBuilder.type(it.getType());
      return targetBuilder.build();
    }).collect(Collectors.toList());

    this.rules = listener.rules.stream().map(it -> {
      TencentLoadBalancerRule rule = new TencentLoadBalancerRule();
      rule.setLocationId(it.getLocationId());
      rule.setDomain(it.getDomain());
      rule.setUrl(it.getUrl());
      rule.setSessionExpireTime(it.getSessionExpireTime());
      rule.setScheduler(it.getScheduler());
      rule.setHealthCheck(TencentLoadBalancerHealthCheck.builder().build());
      rule.getHealthCheck().copyHealthCheck(it.getHealthCheck());
      rule.setCertificate(TencentLoadBalancerCertificate.builder().build());
      rule.getCertificate().copyCertificate(it.getCertificate());
      rule.setTargets(it.getTargets().stream().map(t -> {
        TencentLoadBalancerTarget.TencentLoadBalancerTargetBuilder target = TencentLoadBalancerTarget.builder();
        target.instanceId(t.getInstanceId());
        target.port(t.getPort());
        target.weight(t.getWeight());
        target.type(t.getType());
        return target.build();
      }).collect(Collectors.toList()));
      return rule;
    }).collect(Collectors.toList());
  }
}
