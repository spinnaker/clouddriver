package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.*;

@EqualsAndHashCode
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentLoadBalancer implements LoadBalancer, TencentBasicResource {
  @Builder.Default private final String cloudProvider = TencentCloudProvider.ID;
  @Builder.Default private final String type = TencentCloudProvider.ID;
  private String application;
  private String accountName;
  private String region;
  private String id;
  private String name;
  private String loadBalancerId;
  private String loadBalancerName;
  private String loadBalancerType; // OPEN:公网, INTERNAL:内网;
  private Integer forwardType; // 1:应用型,0:传统型;
  private String vpcId;
  private String subnetId;
  private Integer projectId;
  private String createTime;
  private List<String> loadBalacnerVips;
  private List<String> securityGroups;
  private List<TencentLoadBalancerListener> listeners;
  @Builder.Default private Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();

  @Override
  public String getAccount() {
    return accountName;
  }

  @Override
  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(accountName)
        .withResource(TencentBasicResource.class)
        .deriveMoniker(this);
  }

  @Override
  public String getMonikerName() {
    return name;
  }
}
