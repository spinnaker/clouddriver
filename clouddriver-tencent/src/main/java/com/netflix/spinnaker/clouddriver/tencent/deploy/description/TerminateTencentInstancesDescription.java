package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class TerminateTencentInstancesDescription extends AbstractTencentCredentialsDescription {
  private String serverGroupName;
  private List<String> instanceIds;
  private String region;
  private String accountName;
}
