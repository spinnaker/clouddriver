package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.InstanceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentInstanceType implements InstanceType {
  private String name;
  private String region;
  private String zone;
  private String account;
  private Integer cpu;
  private Integer mem;
  String instanceFamily;
}
