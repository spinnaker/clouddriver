package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class EnableDisableTencentServerGroupDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  String region
  String accountName
}
