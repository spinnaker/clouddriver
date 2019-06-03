package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.KeyPair

class TencentKeyPair implements KeyPair{
  String account
  String region
  String keyId
  String keyName
  String keyFingerprint
}
