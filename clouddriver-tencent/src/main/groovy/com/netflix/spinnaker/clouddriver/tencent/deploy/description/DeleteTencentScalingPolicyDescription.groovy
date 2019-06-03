package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class DeleteTencentScalingPolicyDescription extends AbstractTencentCredentialsDescription {
  String scalingPolicyId
  String serverGroupName
  String region
  String accountName
}
