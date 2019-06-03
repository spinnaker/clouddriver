package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical

@Canonical
class TencentLoadBalancerTarget {
  String instanceId;
  Integer port;
  String type;
  Integer weight;
}
