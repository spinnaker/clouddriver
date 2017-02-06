package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class DisableDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String region
  String serverGroupName
}
