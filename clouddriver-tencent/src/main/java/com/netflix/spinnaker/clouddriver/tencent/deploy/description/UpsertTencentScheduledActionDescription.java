package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import lombok.Data;

@Data
public class UpsertTencentScheduledActionDescription extends AbstractTencentCredentialsDescription {
  private String serverGroupName;
  private String region;
  private String accountName;
  private OperationType operationType;
  private String scheduledActionId;
  private Integer maxSize;
  private Integer minSize;
  private Integer desiredCapacity;
  private String startTime;
  private String endTime;
  private String recurrence;

  public static enum OperationType {
    CREATE,
    MODIFY;
  }
}
