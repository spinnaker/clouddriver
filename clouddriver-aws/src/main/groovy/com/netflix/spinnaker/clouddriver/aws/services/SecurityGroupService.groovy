/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.services

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer

class SecurityGroupService {

  private final AmazonEC2 amazonEC2
  private final SubnetAnalyzer subnetAnalyzer

  SecurityGroupService(AmazonEC2 amazonEC2, SubnetAnalyzer subnetAnalyzer) {
    this.amazonEC2 = amazonEC2
    this.subnetAnalyzer = subnetAnalyzer
  }
/**
   * Find a security group that matches the name of this application.
   *
   * @param applicationName the name of the application to lookup
   * @param subnetPurpose the subnet within which the lookup should take place
   * @return id of Security Group for application
   */
  String getSecurityGroupForApplication(String applicationName, String subnetPurpose = null) {
    try {
      getSecurityGroupIdsWithSubnetPurpose([applicationName], subnetPurpose)?.values()?.getAt(0)
    } catch (SecurityGroupNotFoundException ignore) {
      null
    }
  }

  /**
   * Find security group ids for provided security group names
   *
   * @param securityGroupNames
   * @return Map of security group ids keyed by corresponding security group name
   */
  Map<String, String> getSecurityGroupIds(Collection<String> securityGroupNames, String vpcId = null) {
    if (!securityGroupNames) { return [:] }
    DescribeSecurityGroupsResult result = amazonEC2.describeSecurityGroups()
    Map<String, String> securityGroups = result.securityGroups.findAll { securityGroupNames.contains(it.groupName) && it.vpcId == vpcId }.collectEntries {
      [(it.groupName): it.groupId]
    }
    if (!securityGroups.keySet().containsAll(securityGroupNames)) {
      def missingGroups = securityGroupNames - securityGroups.keySet()
      def ex = new SecurityGroupNotFoundException("Missing security groups: ${missingGroups.join(',')}")
      ex.missingSecurityGroups = missingGroups
      ex.foundSecurityGroups = securityGroups
      throw ex
    }
    securityGroups
  }

  /**
   * Find security group ids for provided security group names
    * @param securityGroupNames names to resolve to ids
   * @param subnetPurpose if not null, will find the vpcId matching the subnet purpose and locate groups in that vpc
   * @return Map of security group ids keyed by corresponding security group name
   */
  Map<String, String> getSecurityGroupIdsWithSubnetPurpose(Collection<String> securityGroupNames, String subnetPurpose = null) {
    String vpcId = subnetPurpose == null ? null : subnetAnalyzer.getVpcIdForSubnetPurpose(subnetPurpose)
    getSecurityGroupIds(securityGroupNames, vpcId)
  }

  /**
   * Create security groups for this this application. Security Group name will equal the application's.
   * (ie. "application") name.
   *
   * @param applicationName
   * @param vpcId
   * @param hasLoadBalancers
   * @return Map of security group ids keyed by corresponding security group name
   */
  Map<String, String> ensureApplicationSecurityGroupsExist(String applicationName, String vpcId, boolean hasLoadBalancers) {
    final appElbSecurityGroupName = "${applicationName}-elb"
    final requiredAppSecurityGroupNames = [applicationName]
    if (hasLoadBalancers) {
      requiredAppSecurityGroupNames.add(appElbSecurityGroupName)
    }
    def nameToId
    try {
      nameToId = getSecurityGroupIds(requiredAppSecurityGroupNames, vpcId)
    } catch (SecurityGroupNotFoundException securityGroupNotFoundException) {
      nameToId = securityGroupNotFoundException.foundSecurityGroups
      def appElbSecurityGroupId = securityGroupNotFoundException.foundSecurityGroups[appElbSecurityGroupName]
      if (vpcId && securityGroupNotFoundException.missingSecurityGroups.contains(appElbSecurityGroupName)) {
        appElbSecurityGroupId = createElbAppSecurityGroup(applicationName, vpcId)
        nameToId[appElbSecurityGroupName] = appElbSecurityGroupId
      }
      if (securityGroupNotFoundException.missingSecurityGroups.contains(applicationName)) {
        final id = createAppSecurityGroup(applicationName, vpcId, appElbSecurityGroupId)
        nameToId[applicationName] = id
      }
    }
    nameToId
  }

  private String createElbAppSecurityGroup(String applicationName, String vpcId) {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(
      groupName: "${applicationName}-elb",
      vpcId: vpcId,
      description: "ELB Security Group for $applicationName"
    )
    final id = amazonEC2.createSecurityGroup(request).groupId
    amazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
      groupId: id,
      ipPermissions: [
        new IpPermission(
          ipProtocol: 'tcp',
          fromPort: 80,
          toPort: 80,
          ipRanges: ['0.0.0.0/0']
        ),
        new IpPermission(
          ipProtocol: 'tcp',
          fromPort: 443,
          toPort: 443,
          ipRanges: ['0.0.0.0/0']
        )
      ]
    ))
    id
  }

  private String createAppSecurityGroup(String applicationName, String vpcId, String appElbSecurityGroupId) {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(
      groupName: applicationName,
      vpcId: vpcId,
      description: "Security Group for $applicationName"
    )
    final id = amazonEC2.createSecurityGroup(request).groupId
    if (appElbSecurityGroupId) {
      amazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
        groupId: id,
        ipPermissions: [
          new IpPermission(
            ipProtocol: 'tcp',
            fromPort: 7001,
            toPort: 7001,
            userIdGroupPairs: [
              new UserIdGroupPair(groupId: appElbSecurityGroupId)
            ]
          ),
          new IpPermission(
            ipProtocol: 'tcp',
            fromPort: 7002,
            toPort: 7002,
            userIdGroupPairs: [
              new UserIdGroupPair(groupId: appElbSecurityGroupId)
            ]
          )
        ]
      ))
    }
    id
  }

}
