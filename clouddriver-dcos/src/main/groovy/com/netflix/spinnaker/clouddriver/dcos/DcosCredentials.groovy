package com.netflix.spinnaker.clouddriver.dcos

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import mesosphere.dcos.client.model.DCOSAuthCredentials

class DcosCredentials implements AccountCredentials<DCOSAuthCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  // TODO This clobbers the Instance#name after a change in gate to merge this credential information directly into
  // instance information. Can't change it, otherwise accounts won't return correctly. We probably want to change how
  // this is structured a bit (look at the NamedAccountCredential stuff that other providers do).
  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()
  final String registry
  final String dcosUrl

  // TODO Ignoring this so credentials aren't exposed. Better way to handle credentials so we don't need to store
  // them here in the first place?
  @JsonIgnore
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

  @JsonIgnore
  @Override
  DCOSAuthCredentials getCredentials() {
    dcosAuthCredentials
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }
}
