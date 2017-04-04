package com.netflix.spinnaker.clouddriver.dcos

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials

import static com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties.*

class DcosCredentials implements AccountCredentials<DCOSAuthCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  // TODO This clobbers the Instance#name after a change in gate to merge this credential information directly into
  // instance information. Can't change it, otherwise accounts won't return correctly. We probably want to change how
  // this is structured a bit (look at the NamedAccountCredential stuff that other providers do).
  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()
  final List<LinkedDockerRegistryConfiguration> dockerRegistries
  final String dcosUrl

  @JsonIgnore
  final Config dcosClientConfig

  DcosCredentials(String name,
                  String environment,
                  String accountType,
                  String dcosUrl,
                  List<LinkedDockerRegistryConfiguration> dockerRegistries,
                  Config dcosClientConfig) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.dcosUrl = dcosUrl
    this.dockerRegistries = dockerRegistries != null ? dockerRegistries : new ArrayList<>()
    this.dcosClientConfig = dcosClientConfig
  }

  @JsonIgnore
  @Override
  DCOSAuthCredentials getCredentials() {
   dcosClientConfig.getCredentials()
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }
}
