package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class DeleteTencentScheduledActionDescription extends AbstractTencentCredentialsDescription {
  String scheduledActionId
  String serverGroupName
  String region
  String accountName
}
