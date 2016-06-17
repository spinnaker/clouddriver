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

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification

/**
 *
 */
class DeployOpenstackAtomicOperationValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  DeployOpenstackAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz

  String account = 'foo'
  String application = 'app1'
  String region = 'region'
  String stack = 'stack1'
  String freeFormDetails = 'test'
  boolean disableRollback = false
  int timeoutMins = 5
  String instanceType = 'm1.small'
  String image = 'ubuntu-latest'
  int maxSize = 5
  int minSize = 3
  String networkId = '1234'
  String poolId = '5678'
  List<String> securityGroups = ['sg1']

  def setup() {
    credz = Mock(OpenstackCredentials)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      _ * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      _ * getCredentials(_) >> credentials
    }
    errors = Mock(Errors)
    validator = new DeployOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
  }

  def "Validate no error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_,_)
  }

  def "Validate application - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: 'asd=adfda=adfdaf', region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate stack - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: '1232=1q434=134134', freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate timeout - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: -1, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate instanceType - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: '', image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate image - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:'', maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate sizing - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: -2, minSize: -1, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    3 * errors.rejectValue(_,_)
  }

  def "Validate network - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: '', poolId: poolId, securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate pool - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: '', securityGroups: securityGroups)
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate security groups - error"() {
    given:
    ServerGroupParameters params = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: [])
    DeployOpenstackAtomicOperationDescription description = new DeployOpenstackAtomicOperationDescription(account: account, application: application, region: region, stack: stack, freeFormDetails: freeFormDetails, disableRollback: disableRollback, timeoutMins: timeoutMins, serverGroupParameters: params)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

}
