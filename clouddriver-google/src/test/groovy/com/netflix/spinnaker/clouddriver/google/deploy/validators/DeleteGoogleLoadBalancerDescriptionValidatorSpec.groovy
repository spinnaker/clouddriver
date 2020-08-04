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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final Long TIMEOUT_SECONDS = 5
  private static final LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  DeleteGoogleLoadBalancerDescriptionValidator validator

  void setupSpec() {
    validator = new DeleteGoogleLoadBalancerDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with full description input"() {
    setup:
      def description = new DeleteGoogleLoadBalancerDescription(
          deleteOperationTimeoutSeconds: TIMEOUT_SECONDS,
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          loadBalancerType: GoogleLoadBalancerType.NETWORK,
          accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with proper minimal description input"() {
    setup:
      def description = new DeleteGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          loadBalancerType: GoogleLoadBalancerType.NETWORK,
          accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "fail validation with bad region"() {
    setup:
      def description = new DeleteGoogleLoadBalancerDescription(
          deleteOperationTimeoutSeconds: TIMEOUT_SECONDS,
          loadBalancerName: LOAD_BALANCER_NAME,
          region: null,
          loadBalancerType: GoogleLoadBalancerType.NETWORK,
          accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("region", _)
  }

  void "null input fails validation"() {
    setup:
      def description = new DeleteGoogleLoadBalancerDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('loadBalancerName', _)
  }
}
