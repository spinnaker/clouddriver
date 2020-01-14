package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.InstanceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentInstanceType implements InstanceType {
  private String name;
  private String region;
  private String zone;
  private String account;
  private Integer cpu;
  private Integer mem;
  String instanceFamily;
}
