package com.netflix.spinnaker.clouddriver.dcos.deploy.description

class ResizeDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
  int capacity
}
