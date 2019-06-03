package com.netflix.spinnaker.clouddriver.tencent.security

class TencentCredentials {
  final String secretId
  final String secretKey

  TencentCredentials(String secretId, String secretKey)
  {
    this.secretId = secretId
    this.secretKey = secretKey
  }
}
