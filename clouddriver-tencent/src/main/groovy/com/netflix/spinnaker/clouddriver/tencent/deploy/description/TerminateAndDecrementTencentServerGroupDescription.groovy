package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class TerminateAndDecrementTencentServerGroupDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  String region
  String instance
}
