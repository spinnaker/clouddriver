package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.Network
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider

class TencentNetwork implements Network{
  String cloudProvider = TencentCloudProvider.ID
  String id
  String name
  String account
  String region
  String cidrBlock
  Boolean isDefault
}
