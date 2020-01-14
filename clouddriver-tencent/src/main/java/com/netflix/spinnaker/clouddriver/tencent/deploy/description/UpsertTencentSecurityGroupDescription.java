package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule;
import java.util.List;
import lombok.Data;

@Data
public class UpsertTencentSecurityGroupDescription extends AbstractTencentCredentialsDescription {
  private String application;
  private String accountName;
  private String region;
  private String securityGroupId;
  private String securityGroupName;
  private String securityGroupDesc;
  private List<TencentSecurityGroupRule> inRules;
  private List<TencentSecurityGroupRule> outRules;
}
