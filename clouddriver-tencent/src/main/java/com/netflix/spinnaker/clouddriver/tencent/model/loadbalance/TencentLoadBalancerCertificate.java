package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentLoadBalancerCertificate {
  public void copyCertificate(TencentLoadBalancerCertificate cert) {
    if (cert != null) {
      this.sslMode = cert.getSslMode();
      this.certId = cert.getCertId();
      this.certCaId = cert.getCertCaId();
      this.certName = cert.getCertName();
      this.certKey = cert.getCertKey();
      this.certContent = cert.getCertContent();
      this.certCaName = cert.getCertCaName();
      this.certCaContent = cert.getCertCaContent();
    }
  }

  private String sslMode;
  private String certId;
  private String certCaId;
  private String certName;
  private String certKey;
  private String certContent;
  private String certCaName;
  private String certCaContent;
}
