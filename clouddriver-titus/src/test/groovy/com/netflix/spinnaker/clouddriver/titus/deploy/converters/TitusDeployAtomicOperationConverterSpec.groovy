/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import spock.lang.Specification
import spock.lang.Subject

class TitusDeployAtomicOperationConverterSpec extends Specification {

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> Stub(NetflixTitusCredentials)
  }

  @Subject
  AtomicOperationConverter atomicOperationConverter = new TitusDeployAtomicOperationConverter(
    accountCredentialsProvider: accountCredentialsProvider,
    objectMapper: new ObjectMapper()
  )

  void 'convertDescription should return a valid TitusDeployDescription'() {
    given:

    Map signedAddressAllocations = [
      addressAllocation     : [
        addressLocation: [region  : "us-east-1", availabilityZone: "us-east-1d",
                          subnetId: "subnet-ffab009"],
        uuid           : "7e571794-4a8b-4335-8be7-c5e3b2660688",
        address        : "192.122.100.100"],
      authoritativePublicKey: "authoritativePublicKeyValue",
      hostPublicKey         : "hostPublicKeyValue",
      hostPublicKeySignature: "hostPublicKeySignatureValue",
      message               : "message",
      messageSignature      : "messageSignatureValue"
    ]

    Map input = [
      application: 'api',
      stack      : 'test',
      details    : '',
      source     : [account: 'test', region: 'us-east-1'],
      subnetType : 'vpc0',
      imageId    : 'api.server:master-201506020033-trusty-7366606',
      capacity   : [desired: 3, min: 2, max: 5],
      resources  : [cpu                     : 2, memory: 4, disk: 4000, ports: [7001], allocateIpAddress: true,
                    signedAddressAllocations: [signedAddressAllocations]],
      env        : ['netflix.environment': 'test'],
      credentials: 'test'
    ]

    when:
    DeployDescription deployDescription = atomicOperationConverter.convertDescription(input)

    then:
    noExceptionThrown()
    deployDescription != null
    deployDescription instanceof TitusDeployDescription
  }

  void 'convertOperation should return a DeployAtomicOperation with TitusDeployDescription'() {
    given:
    Map input = [
      application: 'api',
      stack      : 'test',
      details    : '',
      source     : [account: 'test', region: 'us-east-1'],
      subnetType : 'vpc0',
      imageId    : 'api.server:master-201506020033-trusty-7366606',
      capacity   : [desired: 3, min: 2, max: 5],
      resources  : [cpu: 2, memory: 4, disk: 4000, ports: [7001], allocateIpAddress: true],
      env        : ['netflix.environment': 'test'],
      credentials: 'test'
    ]

    when:
    AtomicOperation atomicOperation = atomicOperationConverter.convertOperation(input)

    then:
    noExceptionThrown()
    atomicOperation != null
    atomicOperation instanceof DeployAtomicOperation
  }
}
