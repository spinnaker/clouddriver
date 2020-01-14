package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.KeyPair;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentKeyPair implements KeyPair {
  private String account;
  private String region;
  private String keyId;
  private String keyName;
  private String keyFingerprint;
}
