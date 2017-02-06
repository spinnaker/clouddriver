package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.LoadBalancer

class DcosCluster implements Cluster, Serializable {
  String name
  String type = Keys.PROVIDER
  String accountName
  Set<DcosServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<DcosServerGroup>())
  Set<LoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<DcosLoadBalancer>())
}
