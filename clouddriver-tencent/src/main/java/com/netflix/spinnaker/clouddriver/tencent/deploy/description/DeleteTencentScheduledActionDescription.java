package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import lombok.Data;

@Data
public class DeleteTencentScheduledActionDescription extends AbstractTencentCredentialsDescription {
  private String scheduledActionId;
  private String serverGroupName;
  private String region;
  private String accountName;
}
