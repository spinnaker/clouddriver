package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import java.util.List;
import lombok.Data;

@Data
public class UpsertTencentLoadBalancerDescription extends AbstractTencentCredentialsDescription {
  private String application;
  private String accountName;
  private String region;
  private String loadBalancerId;
  private String loadBalancerName;
  private String loadBalancerType;
  private Integer forwardType;
  private String vpcId;
  private String subnetId;
  private Integer projectId;
  private List<String> securityGroups;
  private List<TencentLoadBalancerListener> listener;
}
