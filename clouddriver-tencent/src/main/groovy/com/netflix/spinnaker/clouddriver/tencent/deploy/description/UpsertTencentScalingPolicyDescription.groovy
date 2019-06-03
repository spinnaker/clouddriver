package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.tencentcloudapi.as.v20180419.models.MetricAlarm

class UpsertTencentScalingPolicyDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  String region
  String accountName

  OperationType operationType

  String scalingPolicyId
  String adjustmentType
  Integer adjustmentValue

  MetricAlarm metricAlarm
  List<String> notificationUserGroupIds
  Integer cooldown

  enum OperationType {
    CREATE, MODIFY
  }
}
