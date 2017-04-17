package com.netflix.spinnaker.clouddriver.dcos.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials

import static com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties.LinkedDockerRegistryConfiguration

class DcosCredentials implements AccountCredentials<DCOSAuthCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  // TODO This clobbers the Instance#name after a change in gate to merge this credential information directly into
  // instance information. Can't change it, otherwise accounts won't return correctly. We probably want to change how
  // this is structured a bit (look at the NamedAccountCredential stuff that other providers do).
  final String name
  final String environment
  final String accountType
  final List<LinkedDockerRegistryConfiguration> dockerRegistries
  final List<String> requiredGroupMembership
  final String dcosUrl
  final String secretStore

  @JsonIgnore
  final Config dcosClientConfig

  private DcosCredentials(String name,
                          String environment,
                          String accountType,
                          String dcosUrl,
                          List<LinkedDockerRegistryConfiguration> dockerRegistries,
                          List<String> requiredGroupMembership,
                          String secretStore,
                          Config dcosClientConfig) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.dcosUrl = dcosUrl
    this.dockerRegistries = dockerRegistries != null ? dockerRegistries : new ArrayList<>()
    this.requiredGroupMembership = requiredGroupMembership
    this.secretStore = secretStore != null ? secretStore : "default"
    this.dcosClientConfig = dcosClientConfig
  }

  static Builder builder() {
    return new Builder()
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

  static class Builder {
    private String name
    private String environment
    private String accountType
    private List<LinkedDockerRegistryConfiguration> dockerRegistries
    private List<String> requiredGroupMembership
    private String dcosUrl
    private String secretStore
    private Config dcosClientConfig

    Builder name(String name) {
      this.name = name
      this
    }

    Builder environment(String environment) {
      this.environment = environment
      this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      this
    }

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries
      this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      this
    }

    Builder dcosUrl(String dcosUrl) {
      this.dcosUrl = dcosUrl
      this
    }

    Builder secretStore(String secretStore) {
      this.secretStore = secretStore
      this
    }

    Builder dcosClientConfig(Config dcosClientConfig) {
      this.dcosClientConfig = dcosClientConfig
      this
    }

    DcosCredentials build() {
      if (!name) {
        throw new IllegalArgumentException("Account name for DC/OS provider is missing.")
      }

      if (!MarathonPathId.isPartValid(name)) {
        throw new IllegalArgumentException("Account name [${name}] is not valid for the DC/OS provider. Only lowercase letters, numbers, and dashes(-) are allowed.")
      }

      if (!dockerRegistries || dockerRegistries.size() == 0) {
        throw new IllegalArgumentException("Docker registries for DC/OS account [${name}] missing.")
      }

      requiredGroupMembership = requiredGroupMembership ? Collections.unmodifiableList(requiredGroupMembership) : []

      new DcosCredentials(name, environment, accountType, dcosUrl, dockerRegistries, requiredGroupMembership, secretStore, dcosClientConfig)
    }
  }
}
