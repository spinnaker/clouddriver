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
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import spock.lang.Specification

class SecurityGroupServiceSpec extends Specification {

  def securityGroupService = new SecurityGroupService(Mock(AmazonEC2), Mock(SubnetAnalyzer))

  void "should get Security Group for Application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("myApp")

    then:
    result == "sg-123"

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "myApp"),
      new SecurityGroup(groupId: "sg-456", groupName: "yourApp")
    ])
    0 * _
  }

  void "should not get Security Group for Application if it does not exist"() {
    when:
    String result = securityGroupService.getSecurityGroupForApplication("myApp")

    then:
    result == null

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [])
    0 * _
  }

  void "should get Security Group IDs for Names"() {
    when:
    def result = securityGroupService.getSecurityGroupIds(["myApp", "yourApp"])

    then:
    result == [
      myApp: "sg-123",
      yourApp: "sg-456"
    ]

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-123", groupName: "myApp"),
      new SecurityGroup(groupId: "sg-456", groupName: "yourApp")
    ])
    0 * _
  }

  void "should fail to get Security Group IDs for Names if it does not exist"() {
    when:
    securityGroupService.getSecurityGroupIds(["myApp", "yourApp"] as Set<String>)

    then:
    SecurityGroupNotFoundException e = thrown()

    and:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [
      new SecurityGroup(groupId: "sg-456", groupName: "yourApp")
    ])
    0 * _
  }

  void "should create app Security Groups"() {
    when:
    def result = securityGroupService.ensureApplicationSecurityGroupsExist("myApp", "vpc1", true)

    then:
    result == [
      "myApp-elb": "myApp-elb_ID",
      "myApp": "myApp_ID"
    ]

    then:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
      securityGroups: []
    )
    1 * securityGroupService.amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest(
      groupName: "myApp-elb",
      description: "ELB Security Group for myApp",
      vpcId: "vpc1"
    )) >> new CreateSecurityGroupResult(groupId: "myApp-elb_ID")
    1 * securityGroupService.amazonEC2.authorizeSecurityGroupIngress(_) >> {
      assert it[0].groupId == "myApp-elb_ID"
      assert !it[0].ipPermissions.isEmpty()
    }

    then:
    1 * securityGroupService.amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest(
      groupName: "myApp",
      description: "Security Group for myApp",
      vpcId: "vpc1"
    )) >> new CreateSecurityGroupResult(groupId: "myApp_ID")
    1 * securityGroupService.amazonEC2.authorizeSecurityGroupIngress(_) >> {
      assert it[0].groupId == "myApp_ID"
      assert !it[0].ipPermissions.isEmpty()
    }
    0 * _
  }

  void "should not get vpc security groups for an ec2 application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("test", null)

    then:
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [new SecurityGroup(groupId: "sg-1234", groupName: "test", vpcId: "vpc1234")])
    result == null
  }

  void "should get vpc security groups for a vpc application"() {
    when:
    def result = securityGroupService.getSecurityGroupForApplication("test", "internal")

    then:
    1 * securityGroupService.subnetAnalyzer.getVpcIdForSubnetPurpose("internal") >> "vpc1234"
    1 * securityGroupService.amazonEC2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [new SecurityGroup(groupId: "sg-1234", groupName: "test", vpcId: "vpc1234")])
    result == "sg-1234"
  }
}
