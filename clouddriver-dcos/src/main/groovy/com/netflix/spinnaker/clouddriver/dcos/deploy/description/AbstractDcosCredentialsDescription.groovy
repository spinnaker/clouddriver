package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable

abstract class AbstractDcosCredentialsDescription implements CredentialsNameable {
  @JsonIgnore
  DcosCredentials credentials

  @JsonProperty("credentials")
  String getCredentialAccount() {
    this.credentials.name
  }
}
