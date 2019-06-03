package com.netflix.spinnaker.clouddriver.tencent.model.loadbalance

import groovy.transform.Canonical

@Canonical
class TencentLoadBalancerCertificate {
  String sslMode;
  String certId;
  String certCaId;
  String certName;
  String certKey;
  String certContent;
  String certCaName;
  String certCaContent;

  void copyCertificate(TencentLoadBalancerCertificate cert) {
    if (cert != null) {
      this.sslMode = cert.sslMode
      this.certId = cert.certId
      this.certCaId = cert.certCaId
      this.certName = cert.certName
      this.certKey = cert.certKey
      this.certContent = cert.certContent
      this.certCaName = cert.certCaName
      this.certCaContent = cert.certCaContent
    }
  }
}
