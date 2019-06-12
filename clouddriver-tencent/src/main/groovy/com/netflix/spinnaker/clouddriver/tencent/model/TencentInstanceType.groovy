package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.InstanceType
import groovy.transform.Canonical

@Canonical
class TencentInstanceType implements InstanceType {
  String name
  String region
  String zone
  String account
  Integer cpu
  Integer mem
  String instanceFamily
}
