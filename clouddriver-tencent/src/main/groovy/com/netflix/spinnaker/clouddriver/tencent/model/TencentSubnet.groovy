package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.Subnet
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider

class TencentSubnet implements Subnet {
  final String type = TencentCloudProvider.ID
  String name
  String id
  String account
  String region
  String vpcId
  String cidrBlock
  Boolean isDefault
  String zone
  String purpose
}
