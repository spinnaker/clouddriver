package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeleteTencentLoadBalancerDescription extends AbstractTencentCredentialsDescription {
  String application
  String accountName
  String region
  String loadBalancerId
  List<TencentLoadBalancerListener> listener  //listeners
}
