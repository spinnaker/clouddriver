package com.netflix.spinnaker.clouddriver.tencent.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Immutable


@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class TencentSecurityGroup implements SecurityGroup{
  final String type = TencentCloudProvider.ID
  final String cloudProvider = TencentCloudProvider.ID
  final String id             //securityGroupId
  final String name           //securityGroupName
  final String description
  final String application
  final String accountName
  final String region

  final Set<Rule> inboundRules = []
  final Set<Rule> outboundRules = []

  List<TencentSecurityGroupRule> inRules
  List<TencentSecurityGroupRule> outRules

  void setMoniker(Moniker _ignored) {}

  @Override
  SecurityGroupSummary getSummary() {
    new TencentSecurityGroupSummary(name: name, id: id)
  }
}
