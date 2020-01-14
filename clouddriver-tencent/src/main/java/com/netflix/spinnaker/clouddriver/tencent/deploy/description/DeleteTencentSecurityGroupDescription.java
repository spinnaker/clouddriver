package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DeleteTencentSecurityGroupDescription extends AbstractTencentCredentialsDescription {
  private String accountName;
  private String region;
  private String securityGroupId;
}
