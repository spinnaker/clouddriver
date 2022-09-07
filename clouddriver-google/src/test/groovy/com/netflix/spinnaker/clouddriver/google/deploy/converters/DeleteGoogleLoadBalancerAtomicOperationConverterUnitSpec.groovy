/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.DeleteGoogleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final long TIMEOUT_SECONDS = 5
  private static final LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeleteGoogleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteGoogleLoadBalancerAtomicOperationConverter()
    def credentialsRepository = Mock(CredentialsRepository)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    credentialsRepository.getOne(_) >> mockCredentials
    converter.credentialsRepository = credentialsRepository
  }

  void "deleteGoogleLoadBalancerDescription type returns DeleteGoogleLoadBalancerDescription and DeleteGoogleLoadBalancerAtomicOperation"() {
    setup:
      def input = [deleteOperationTimeoutSeconds: TIMEOUT_SECONDS,
                   loadBalancerName: LOAD_BALANCER_NAME,
                   region: REGION,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeleteGoogleLoadBalancerDescription
      description.deleteOperationTimeoutSeconds == TIMEOUT_SECONDS
      description.loadBalancerName == LOAD_BALANCER_NAME

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeleteGoogleLoadBalancerAtomicOperation
  }
}
