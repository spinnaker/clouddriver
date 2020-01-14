package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DeleteTencentLoadBalancerDescription extends AbstractTencentCredentialsDescription {
  private String application;
  private String accountName;
  private String region;
  private String loadBalancerId;
  private List<TencentLoadBalancerListener> listener;
}
