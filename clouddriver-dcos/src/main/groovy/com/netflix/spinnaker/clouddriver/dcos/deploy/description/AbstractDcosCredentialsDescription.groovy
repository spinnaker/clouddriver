package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable

abstract class AbstractDcosCredentialsDescription implements CredentialsNameable {
  String account

  //@JsonIgnore
  DcosCredentials credentials

  //@JsonProperty("credentials")
  //String getCredentialAccount() {
  //  this.credentials.name
  //}
}
