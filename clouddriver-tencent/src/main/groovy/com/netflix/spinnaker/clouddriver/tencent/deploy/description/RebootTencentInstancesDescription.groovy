package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class RebootTencentInstancesDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  List<String> instanceIds
  String region
  String accountName
}
