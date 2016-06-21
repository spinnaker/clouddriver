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
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@GoogleOperation(AtomicOperations.UPDATE_LAUNCH_CONFIG)
@Component("modifyGoogleServerGroupInstanceTemplateDescriptionValidator")
class ModifyGoogleServerGroupInstanceTemplateDescriptionValidator extends DescriptionValidator<ModifyGoogleServerGroupInstanceTemplateDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Override
  void validate(List priorDescriptions, ModifyGoogleServerGroupInstanceTemplateDescription description, Errors errors) {
    def helper = new StandardGceAttributeValidator("modifyGoogleServerGroupInstanceTemplateDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateInstanceTypeDisks(googleDeployDefaults.determineInstanceTypeDisk(description.instanceType),
                                     description.disks)
    helper.validateAuthScopes(description.authScopes)
    helper.validateRegion(description.region)
    helper.validateServerGroupName(description.serverGroupName)
    if (description.instanceType) {
      helper.validateInstanceType(description.instanceType, description.region)
    }
  }
}
