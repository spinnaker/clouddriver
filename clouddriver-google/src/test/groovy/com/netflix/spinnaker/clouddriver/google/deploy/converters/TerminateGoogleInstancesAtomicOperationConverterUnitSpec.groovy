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
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.TerminateGoogleInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class TerminateGoogleInstancesAtomicOperationConverterUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final ZONE = "us-central1-b"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  TerminateGoogleInstancesAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new TerminateGoogleInstancesAtomicOperationConverter()
    def credentialsRepository = Mock(CredentialsRepository)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    credentialsRepository.getOne(_) >> mockCredentials
    converter.credentialsRepository = credentialsRepository
  }

  void "terminateGoogleInstancesDescription type returns TerminateGoogleInstancesDescription and TerminateGoogleInstancesAtomicOperation"() {
    setup:
      def input = [zone: ZONE, credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof TerminateGoogleInstancesDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof TerminateGoogleInstancesAtomicOperation
  }
}
