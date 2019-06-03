package com.netflix.spinnaker.clouddriver.tencent.config

import groovy.transform.ToString

@ToString(includeNames = true)
class TencentConfigurationProperties {
  @ToString(includeNames = true)
  static class ManagedAccount {
    String name
    String environment
    String accountType
    String project
    String secretId
    String secretKey
    List<String> regions
  }

  List<ManagedAccount> accounts = []
}
