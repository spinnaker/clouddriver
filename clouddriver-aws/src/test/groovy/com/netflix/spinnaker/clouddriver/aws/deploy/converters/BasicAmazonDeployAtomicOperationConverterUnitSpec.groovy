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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.amazonaws.services.ec2.AmazonEC2
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  RegionScopedProviderFactory regionScopedProviderFactory

  @Shared
  BasicAmazonDeployAtomicOperationConverter converter

  RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
    Mock(RegionScopedProviderFactory.RegionScopedProvider)

  SecurityGroupService securityGroupService = new SecurityGroupService(Mock(AmazonEC2), Mock(SubnetAnalyzer))

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials('test') >> Stub(NetflixAmazonCredentials)
    }
    this.regionScopedProviderFactory = Stub(RegionScopedProviderFactory)
    this.converter = new BasicAmazonDeployAtomicOperationConverter(objectMapper: mapper,
      accountCredentialsProvider: accountCredentialsProvider,
      regionScopedProviderFactory: regionScopedProviderFactory)
  }

  void "converts securityGroups to securityGroupNames"() {
    setup:
    def securityGroups = ["sg-12345678", "sg-87654321"]
    def input = [application      : "asgard", amiName: "ami-000", stack: "asgard-test", instanceType: "m3.medium",
                 availabilityZones: ["us-west-1": ["us-west-1a"]], capacity: [min: 1, max: 2, desired: 5],
                 credentials      : "test", securityGroups: securityGroups]

    regionScopedProviderFactory.forRegion(_ as NetflixAmazonCredentials, _ as String) >> regionScopedProvider
    regionScopedProvider.getSecurityGroupService() >> securityGroupService
    securityGroupService.getSecurityGroupNamesFromIds(_ as Collection<String>) >> [(input.application): input.securityGroups[0]]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof BasicAmazonDeployDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployAtomicOperation
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [application      : "asgard", amiName: "ami-000", stack: "asgard-test", instanceType: "m3.medium",
                 availabilityZones: ["us-west-1": ["us-west-1a"]], capacity: [min: 1, max: 2, desired: 5],
                 credentials      : "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof BasicAmazonDeployDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployAtomicOperation
  }

  void "should not fail to serialize unknown properties"() {
    setup:
    def input = [application: application, unknownProp: "this", credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description.application == application

    where:
    application = "kato"
  }

  void "should probably convert capacity to ints"() {
    setup:
    def input = [application: "app", credentials: 'test', capacity: [min: min, max: max, desired: desired]]

    when:
    def description = converter.convertDescription(input)

    then:
    description.capacity.min == min as int
    description.capacity.max == max as int
    description.capacity.desired == desired as int

    where:
    min = "5"
    max = "10"
    desired = "8"
  }
}
