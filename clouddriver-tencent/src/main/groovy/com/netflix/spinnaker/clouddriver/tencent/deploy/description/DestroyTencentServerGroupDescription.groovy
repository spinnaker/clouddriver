package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DestroyTencentServerGroupDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  String region
  String accountName
}
