package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.tencentcloudapi.as.v20180419.models.MetricAlarm;
import java.util.List;
import lombok.Data;

@Data
public class UpsertTencentScalingPolicyDescription extends AbstractTencentCredentialsDescription {
  private String serverGroupName;
  private String region;
  private String accountName;
  private OperationType operationType;
  private String scalingPolicyId;
  private String adjustmentType;
  private Integer adjustmentValue;
  private MetricAlarm metricAlarm;
  private List<String> notificationUserGroupIds;
  private Integer cooldown;

  public static enum OperationType {
    CREATE,
    MODIFY;
  }
}
