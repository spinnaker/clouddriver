package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

class TencentLoadBalancerTargetHealth {
  String instanceId
  Integer port
  Boolean healthStatus
  String loadBalancerId
  String listenerId
  String locationId
}
