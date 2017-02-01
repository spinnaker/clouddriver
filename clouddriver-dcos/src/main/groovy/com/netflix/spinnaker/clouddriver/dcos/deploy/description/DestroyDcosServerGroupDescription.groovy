package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class DestroyDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
}
