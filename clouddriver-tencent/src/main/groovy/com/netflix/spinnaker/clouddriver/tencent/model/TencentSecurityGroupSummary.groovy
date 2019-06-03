package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable

@Immutable
@EqualsAndHashCode(includes = ['id'], cache = true)
class TencentSecurityGroupSummary implements SecurityGroupSummary{
  String name
  String id
}
