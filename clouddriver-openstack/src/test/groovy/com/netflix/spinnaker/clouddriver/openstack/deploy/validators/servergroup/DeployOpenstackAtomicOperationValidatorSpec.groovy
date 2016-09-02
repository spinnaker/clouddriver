/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class DeployOpenstackAtomicOperationValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  DeployOpenstackAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz
  OpenstackClientProvider clientProvider

  String account = 'foo'
  String application = 'app1'
  String region = 'r1'
  String stack = 'stack1'
  String freeFormDetails = 'test'
  boolean disableRollback = false
  int timeoutMins = 5
  String instanceType = 'm1.small'
  String image = 'ubuntu-latest'
  int maxSize = 5
  int minSize = 3
  int desiredSize = 4
  String subnetId = '1234'
  List<String> loadBalancerIds = ['5678']
  List<String> securityGroups = ['sg1']

  def setup() {
    clientProvider = Mock(OpenstackClientProvider) {
      getAllRegions() >> ['r1']
    }
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackProviderFactory.createProvider(credentials) >> clientProvider
    credz = new OpenstackCredentials(credentials)
    errors = Mock(Errors)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      _ * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      _ * getCredentials(_) >> credentials
    }
    validator = new DeployOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
  }

  def "Validate no error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_,_)
  }

  def "Validate with scaling parameters"() {
    given:
    ServerGroupParameters.Scaler scaleup = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: 1, period: 60, threshold: 50)
    ServerGroupParameters.Scaler scaledown = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: -1, period: 600, threshold: 15)
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups, autoscalingType: ServerGroupParameters.AutoscalingType.CPU, scaleup: scaleup, scaledown: scaledown)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_,_)
  }

  def "Validate with invalid scaling parameters"() {
    given:
    ServerGroupParameters.Scaler scaleup = new ServerGroupParameters.Scaler(cooldown: -1, adjustment: 10, period: -1, threshold: -1)
    ServerGroupParameters.Scaler scaledown = new ServerGroupParameters.Scaler(cooldown: -1, adjustment: -15, period: -1, threshold: -1)
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups, autoscalingType: ServerGroupParameters.AutoscalingType.CPU, scaleup: scaleup, scaledown: scaledown)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    8 * errors.rejectValue(_,_)
  }

  @Unroll
  def "Validate create missing required core field - #attribute"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)
    if (attribute != 'stack') {
      description."$attribute" = ''
    } else {
      description."$attribute" = '1-2-3'
    }

    when:
    validator.validate([], description, errors)

    then:
    times * errors.rejectValue(_,_)

    where:
    attribute << ['application', 'stack']
    times << [2,1]
  }

  @Unroll
  def "Validate create missing required template field - #attribute"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)
    description.serverGroupParameters."$attribute" = null

    when:
    validator.validate([], description, errors)

    then:
    times * errors.rejectValue(_,_)

    where:
    attribute << ['instanceType', 'image', 'maxSize', 'minSize', 'desiredSize', 'subnetId', 'loadBalancers', 'securityGroups']
    times << [1,1,2,3,2,1,1,1]
  }

  def "Validate sizing - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: -2, minSize: -1, desiredSize: -3, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    5 * errors.rejectValue(_,_)
  }

  @Unroll
  def "validate userData parameter"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, subnetId: subnetId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params, userData: userData, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    times * errors.rejectValue(_,_)

    where:
    userData            | times
    'http://foobar.com' | 0
    '#!/bin/bash'       | 0
    'http$$$asdfdfadf'  | 0
    null                | 0
  }

}
