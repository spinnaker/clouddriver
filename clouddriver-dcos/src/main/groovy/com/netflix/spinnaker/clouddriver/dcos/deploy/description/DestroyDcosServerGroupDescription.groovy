package com.netflix.spinnaker.clouddriver.dcos.deploy.description

class DestroyDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
}
