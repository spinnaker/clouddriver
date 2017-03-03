package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import mesosphere.dcos.client.model.DCOSAuthCredentials

class DcosCredentials implements AccountCredentials<DCOSAuthCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()
  final String registry
  final String dcosUrl
  final DCOSAuthCredentials dcosAuthCredentials

  DcosCredentials(String name,
                  String environment,
                  String accountType,
                  String dcosUrl,
                  DCOSAuthCredentials dcosAuthCredentials) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.registry = registry
    this.dcosUrl = dcosUrl
    this.dcosAuthCredentials = dcosAuthCredentials
  }

  @Override
  DCOSAuthCredentials getCredentials() {
    dcosAuthCredentials
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }
}
