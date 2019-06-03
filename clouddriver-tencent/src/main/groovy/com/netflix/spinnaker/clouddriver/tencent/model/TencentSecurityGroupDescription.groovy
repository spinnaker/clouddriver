package com.netflix.spinnaker.clouddriver.tencent.model

class TencentSecurityGroupDescription {
  String securityGroupId
  String securityGroupName
  String securityGroupDesc
  List<TencentSecurityGroupRule> inRules
  List<TencentSecurityGroupRule> outRules
  long lastReadTime
}
