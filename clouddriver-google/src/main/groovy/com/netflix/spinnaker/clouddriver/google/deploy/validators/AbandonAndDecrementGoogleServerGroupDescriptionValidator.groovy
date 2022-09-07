/*
 * Copyright 2015 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.AbandonAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component("abandonAndDecrementGoogleServerGroupDescriptionValidator")
class AbandonAndDecrementGoogleServerGroupDescriptionValidator extends DescriptionValidator<AbandonAndDecrementGoogleServerGroupDescription> {
  @Autowired
  CredentialsRepository<GoogleNamedAccountCredentials> credentialsRepository

  @Override
  void validate(List priorDescriptions, AbandonAndDecrementGoogleServerGroupDescription description, ValidationErrors errors) {
    StandardGceAttributeValidator helper = new StandardGceAttributeValidator("abandonAndDecrementGoogleServerGroupDescription", errors)

    helper.validateCredentials(description.accountName, credentialsRepository)
    helper.validateRegion(description.region, description.credentials)
    helper.validateInstanceIds(description.instanceIds)
    helper.validateServerGroupName(description.serverGroupName)
  }
}
