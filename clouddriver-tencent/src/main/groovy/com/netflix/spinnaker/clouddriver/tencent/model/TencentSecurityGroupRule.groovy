package com.netflix.spinnaker.clouddriver.tencent.model

class TencentSecurityGroupRule {
  Integer index        //rule index
  String protocol      //TCP, UDP, ICMP, GRE, ALL
  String port          //all, 离散port,  range
  String cidrBlock
  String action        //ACCEPT or DROP
}
