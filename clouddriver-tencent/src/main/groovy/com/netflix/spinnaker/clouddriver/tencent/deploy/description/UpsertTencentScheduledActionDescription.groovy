package com.netflix.spinnaker.clouddriver.tencent.deploy.description

class UpsertTencentScheduledActionDescription extends AbstractTencentCredentialsDescription {
  String serverGroupName
  String region
  String accountName

  OperationType operationType

  String scheduledActionId
  Integer maxSize
  Integer minSize
  Integer desiredCapacity

  String startTime
  String endTime
  String recurrence

  enum OperationType {
    CREATE, MODIFY
  }
}
