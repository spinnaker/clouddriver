package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class UpsertTencentSecurityGroupDescription extends AbstractTencentCredentialsDescription {
  String application
  String accountName
  String region

  String securityGroupId
  String securityGroupName
  String securityGroupDesc
  List<TencentSecurityGroupRule> inRules
  List<TencentSecurityGroupRule> outRules
}
