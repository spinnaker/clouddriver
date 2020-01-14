package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentCluster implements Cluster {
  private final String type = TencentCloudProvider.ID;
  private String name;
  private String accountName;

  @Builder.Default
  private Set<TencentServerGroup> serverGroups =
      Collections.synchronizedSet(new HashSet<TencentServerGroup>());

  @Builder.Default
  private Set<TencentLoadBalancer> loadBalancers =
      Collections.synchronizedSet(new HashSet<TencentLoadBalancer>());
}
