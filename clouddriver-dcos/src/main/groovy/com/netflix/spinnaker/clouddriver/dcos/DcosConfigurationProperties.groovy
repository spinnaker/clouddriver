package com.netflix.spinnaker.clouddriver.dcos

class DcosConfigurationProperties {
  public static final int ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT = 300
  public static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS = 8

  List<Account> accounts = []

  int asyncOperationTimeoutSecondsDefault = ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT
  int asyncOperationMaxPollingIntervalSeconds = ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS

  // TODO where best to enforce this as required, besides just failing during LB creation.
  LoadBalancerConfig loadBalancer

  static class Account {
    String name
    String environment
    String accountType
    String dcosUrl
    String uid
    String password
    String serviceKey
  }

  static class LoadBalancerConfig {
    String image
    String serviceAccountSecret
  }
}
