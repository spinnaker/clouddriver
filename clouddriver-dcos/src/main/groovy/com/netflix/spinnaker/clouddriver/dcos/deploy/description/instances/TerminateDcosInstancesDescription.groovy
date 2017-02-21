package com.netflix.spinnaker.clouddriver.dcos.deploy.description.instances

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class TerminateDcosInstancesDescription extends AbstractDcosCredentialsDescription {
  String appId
  String hostId
  List<String> taskIds = new ArrayList<>()
  boolean force
  boolean wipe
}
