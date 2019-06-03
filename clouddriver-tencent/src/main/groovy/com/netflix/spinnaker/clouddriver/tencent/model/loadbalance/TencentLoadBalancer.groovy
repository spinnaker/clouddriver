package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance


import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource
import com.netflix.spinnaker.moniker.Moniker


class TencentLoadBalancer implements LoadBalancer, TencentBasicResource{
  final String cloudProvider = TencentCloudProvider.ID
  final String type = TencentCloudProvider.ID
  String application
  String accountName
  String region

  String id
  String name
  String loadBalancerId
  String loadBalancerName
  String loadBalancerType     //OPEN:公网, INTERNAL:内网
  Integer forwardType         //1:应用型,0:传统型
  String vpcId
  String subnetId
  Integer projectId
  String createTime
  List<String> loadBalacnerVips
  List<String> securityGroups
  List<TencentLoadBalancerListener> listeners

  Set<LoadBalancerServerGroup> serverGroups = []

  @Override
  String getAccount() {
    accountName
  }

  @Override
  Moniker getMoniker() {
    return NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(accountName)
      .withResource(TencentBasicResource)
      .deriveMoniker(this)
  }

  @Override
  String getMonikerName() {
    name
  }

  @Override
  boolean equals(Object o) {
    if (!(o instanceof TencentLoadBalancer)) {
      return false
    }
    TencentLoadBalancer other = (TencentLoadBalancer)o
    other.getAccount() == this.getAccount() && other.getName() == this.getName() && other.getType() == this.getType() && other.getId() == this.getId() && other.getRegion() == this.getRegion()
  }

  @Override
  int hashCode() {
    //getAccount().hashCode() + getId().hashCode() + getType().hashCode() + region.hashCode()
    getId().hashCode() + getType().hashCode()
  }
}
