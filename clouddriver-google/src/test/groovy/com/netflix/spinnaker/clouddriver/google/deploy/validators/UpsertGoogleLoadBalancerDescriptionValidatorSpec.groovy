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

import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"
  private static final INSTANCE = "inst"

  @Shared
  UpsertGoogleLoadBalancerDescriptionValidator validator

  void setupSpec() {
    validator = new UpsertGoogleLoadBalancerDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).regionLookupEnabled(false).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new UpsertGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          instances: [INSTANCE],
          healthCheck: [
              port: 8080,
              checkIntervalSec: 5,
              healthyThreshold: 2,
              unhealthyThreshold: 2,
              timeoutSec: 5,
              requestPath: "/"
          ],
          ipAddress: "1.1.1.1",
          ipProtocol: "TCP",
          portRange: "80-82")
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation without health checks and without IP protocol"() {
    setup:
      def description = new UpsertGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          instances: [INSTANCE])
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "improper IP protocol fails validation"() {
    setup:
      def description = new UpsertGoogleLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          instances: [INSTANCE],
          healthCheck: [
              port: 8080,
              checkIntervalSec: 5,
              healthyThreshold: 2,
              unhealthyThreshold: 2,
              timeoutSec: 5,
              requestPath: "/"
          ],
          ipAddress: "1.1.1.1",
          ipProtocol: "ABC",
          portRange: "80-82")
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('ipProtocol', "upsertGoogleLoadBalancerDescription.ipProtocol.notSupported")
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertGoogleLoadBalancerDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('loadBalancerName', _)
      1 * errors.rejectValue('region', _)
  }
}
