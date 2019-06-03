package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials

class AbstractTencentCredentialsDescription implements CredentialsNameable {
  TencentNamedAccountCredentials credentials
}
