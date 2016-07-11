/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.validators
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertCloudFoundryLoadBalancerDescriptionValidatorSpec extends Specification {

  private static final LOAD_BALANCER_NAME = "service-registry"
  private static final REGION = "some-region"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  UpsertCloudFoundryLoadBalancerDescriptionValidator validator

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save(ACCOUNT_NAME, TestCredential.named(ACCOUNT_NAME))

    validator = new UpsertCloudFoundryLoadBalancerDescriptionValidator(accountCredentialsProvider: credentialsProvider)
  }

  void "should validate upsert load balancer with proper inputs"() {
    setup:
      def description = new UpsertCloudFoundryLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          region: REGION,
          credentials: TestCredential.named(ACCOUNT_NAME))
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails upsert load balancer validation"() {
    setup:
      def description = new UpsertCloudFoundryLoadBalancerDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('loadBalancerName.empty', _)
      1 * errors.rejectValue('region', _)
  }

}
