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
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.DisableGoogleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DisableGoogleServerGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"

  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DisableGoogleServerGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DisableGoogleServerGroupAtomicOperationConverter()
    def credentialsRepository = Mock(CredentialsRepository)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    credentialsRepository.getOne(_) >> mockCredentials
    converter.credentialsRepository = credentialsRepository
  }

  void "disableGoogleServerGroupDescription type returns EnableDisableGoogleServerGroupDescription and DisableGoogleServerGroupAtomicOperation"() {
    setup:
      def input = [serverGroupName: SERVER_GROUP_NAME,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof EnableDisableGoogleServerGroupDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DisableGoogleServerGroupAtomicOperation
  }
}
