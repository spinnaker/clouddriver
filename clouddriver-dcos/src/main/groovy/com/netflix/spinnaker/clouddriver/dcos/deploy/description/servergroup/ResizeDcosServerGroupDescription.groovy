package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class ResizeDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
  Integer targetSize
}
