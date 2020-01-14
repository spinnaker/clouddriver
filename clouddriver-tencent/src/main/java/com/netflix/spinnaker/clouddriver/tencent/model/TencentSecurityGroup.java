package com.netflix.spinnaker.clouddriver.tencent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TencentSecurityGroup implements SecurityGroup {
  private final String type = TencentCloudProvider.ID;
  private final String cloudProvider = TencentCloudProvider.ID;
  private final String id; // securityGroupId
  private final String name; // securityGroupName
  private final String description;
  private final String application;
  private final String accountName;
  private final String region;

  private final Set<Rule> inboundRules = new HashSet<>();
  private final Set<Rule> outboundRules = new HashSet<>();

  private List<TencentSecurityGroupRule> inRules;
  private List<TencentSecurityGroupRule> outRules;

  void setMoniker(Moniker _ignored) {}

  @Override
  public SecurityGroupSummary getSummary() {
    return new TencentSecurityGroupSummary(name, id);
  }
}
