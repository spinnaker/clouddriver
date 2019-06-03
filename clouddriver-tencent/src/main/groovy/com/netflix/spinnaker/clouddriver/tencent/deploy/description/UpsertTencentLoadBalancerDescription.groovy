package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class UpsertTencentLoadBalancerDescription extends AbstractTencentCredentialsDescription {
  String application
  String accountName
  String region

  String loadBalancerId
  String loadBalancerName
  String loadBalancerType     //OPEN:公网, INTERNAL:内网
  Integer forwardType     //1:应用型,0:传统型
  String vpcId
  String subnetId
  Integer projectId
  List<String> securityGroups

  //listener, rule, target
  List<TencentLoadBalancerListener> listener  //listeners
}
