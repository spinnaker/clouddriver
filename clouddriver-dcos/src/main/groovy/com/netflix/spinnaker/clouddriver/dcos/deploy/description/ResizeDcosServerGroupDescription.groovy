package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import groovy.transform.Canonical

class ResizeDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
  String serverGroupName
  Capacity capacity

  @Canonical
  static class Capacity {
    int min
    int max
    int desired
  }
}
