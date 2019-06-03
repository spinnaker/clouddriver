package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical

@Canonical
class TencentLoadBalancerRuleTarget {
  String locationId;
  String domain;
  String url;
  List<TencentLoadBalancerTarget> targets;
}
