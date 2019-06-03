package com.netflix.spinnaker.clouddriver.tencent.security;

public class TencentCredentials {
  public TencentCredentials(String secretId, String secretKey) {
    this.secretId = secretId;
    this.secretKey = secretKey;
  }

  public final String getSecretId() {
    return secretId;
  }

  public final String getSecretKey() {
    return secretKey;
  }

  private final String secretId;
  private final String secretKey;
}
