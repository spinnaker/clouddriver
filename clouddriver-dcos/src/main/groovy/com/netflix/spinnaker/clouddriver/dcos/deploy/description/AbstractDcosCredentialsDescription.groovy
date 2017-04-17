package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable

abstract class AbstractDcosCredentialsDescription implements CredentialsNameable {
  String account
  DcosCredentials credentials
}
