package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeleteTencentSecurityGroupDescription extends AbstractTencentCredentialsDescription {
  String accountName
  String region
  String securityGroupId
}
