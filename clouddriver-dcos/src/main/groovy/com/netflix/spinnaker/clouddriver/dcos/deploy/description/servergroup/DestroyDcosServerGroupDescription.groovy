package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class DestroyDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
}
