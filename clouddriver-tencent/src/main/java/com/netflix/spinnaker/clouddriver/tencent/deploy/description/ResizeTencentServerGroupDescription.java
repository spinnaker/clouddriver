package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ResizeTencentServerGroupDescription extends AbstractTencentCredentialsDescription {
  Capacity capacity;
  String serverGroupName;
  String region;
  String accountName;

  @Data
  public static class Capacity {
    Integer min;
    Integer max;
    Integer desired;
  }
}
