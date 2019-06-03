package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class ResizeTencentServerGroupDescription extends AbstractTencentCredentialsDescription {
  Capacity capacity
  String serverGroupName
  String region
  String accountName

  static class Capacity {
    Integer min
    Integer max
    Integer desired
  }
}
