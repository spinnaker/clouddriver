package com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class TerminateDcosInstancesAndDecrementDescription extends AbstractDcosCredentialsDescription {
  String appId
  String hostId
  List<String> taskIds = new ArrayList<>()
  boolean force
}
