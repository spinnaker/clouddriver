package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import lombok.Data;

@Data
public class TerminateAndDecrementTencentServerGroupDescription
    extends AbstractTencentCredentialsDescription {
  private String serverGroupName;
  private String region;
  private String instance;
}
