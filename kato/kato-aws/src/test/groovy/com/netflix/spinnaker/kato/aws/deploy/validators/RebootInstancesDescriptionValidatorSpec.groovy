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

package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.aws.deploy.description.RebootInstancesDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class RebootInstancesDescriptionValidatorSpec extends AbstractConfiguredRegionAndInstanceIdsValidatorSpec {
  @Override
  DescriptionValidator getDescriptionValidator() {
    return new RebootInstancesDescriptionValidator()
  }

  @Override
  AbstractAmazonCredentialsDescription getDescription() {
    return new RebootInstancesDescription()
  }
}
