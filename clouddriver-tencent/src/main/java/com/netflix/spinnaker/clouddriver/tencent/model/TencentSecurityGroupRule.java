package com.netflix.spinnaker.clouddriver.tencent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentSecurityGroupRule {
  private Integer index;        //rule index
  private String protocol;      //TCP, UDP, ICMP, GRE, ALL
  private String port;          //all, 离散port,  range
  private String cidrBlock;
  private String action;        //ACCEPT or DROP
}
