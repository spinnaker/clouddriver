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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component("allowLaunchDescriptionValidator")
class AllowLaunchDescriptionValidator extends DescriptionValidator<AllowLaunchDescription> {
  @Autowired
  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository

  @Override
  void validate(List priorDescriptions, AllowLaunchDescription description, ValidationErrors errors) {
    if (!description.amiName) {
      errors.rejectValue("amiName", "allowLaunchDescription.amiName.empty")
    }
    if (!description.region) {
      errors.rejectValue("region", "allowLaunchDescription.region.empty")
    }
    if (!description.targetAccount) {
      errors.rejectValue("targetAccount", "allowLaunchDescription.targetAccount.empty")
    } else if (credentialsRepository.getOne(description.targetAccount) == null) {
      errors.rejectValue("targetAccount", "allowLaunchDescription.targetAccount.not.configured")
    }
  }
}
