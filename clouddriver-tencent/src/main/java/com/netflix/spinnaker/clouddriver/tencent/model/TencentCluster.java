package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
public class TencentCluster implements Cluster {
  private final String type = TencentCloudProvider.ID;
  private String name;
  private String accountName;
  private Set<TencentServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<TencentServerGroup>());
  private Set<TencentLoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<TencentLoadBalancer>());
}
