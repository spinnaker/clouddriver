package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import groovy.transform.Canonical

class CloneDcosServerGroupDescription extends DeployDcosServerGroupDescription{
  DcosCloneServerGroupSource source
}

@Canonical
class DcosCloneServerGroupSource extends AbstractDcosServerGroupDescription {
  String account
}
