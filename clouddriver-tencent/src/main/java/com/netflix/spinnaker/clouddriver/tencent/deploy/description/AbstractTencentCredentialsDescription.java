package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;

public class AbstractTencentCredentialsDescription implements CredentialsNameable {
  public TencentNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(TencentNamedAccountCredentials credentials) {
    this.credentials = credentials;
  }

  private TencentNamedAccountCredentials credentials;
}
