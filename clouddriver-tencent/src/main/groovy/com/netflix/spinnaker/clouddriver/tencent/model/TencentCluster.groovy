package com.netflix.spinnaker.clouddriver.tencent.model


import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class TencentCluster implements Cluster {
  final String type = TencentCloudProvider.ID
  String name
  String accountName
  Set<TencentServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<TencentServerGroup>())
  Set<TencentLoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<TencentLoadBalancer>())
}
