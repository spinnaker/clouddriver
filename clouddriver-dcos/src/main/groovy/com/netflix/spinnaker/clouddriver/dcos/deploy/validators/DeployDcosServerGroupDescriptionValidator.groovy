/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.CREATE_SERVER_GROUP)
class DeployDcosServerGroupDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DeployDcosServerGroupDescription> {

  @Autowired
  DeployDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "deployDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DeployDcosServerGroupDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.application) {
      errors.rejectValue "application", "titusDeployDescription.application.empty"
    }

    if (!description.imageId) {
      errors.rejectValue "imageId", "titusDeployDescription.imageId.empty"
    }

    if (!description.capacity) {
      errors.rejectValue "capacity", "titusDeployDescription.capacity.invalid"
    } else {
      if (description.capacity.min < 0) {
        errors.rejectValue "capacity", "titusDeployDescription.capacity.min.invalid"
      }

      if (description.capacity.max < 0) {
        errors.rejectValue "capacity", "titusDeployDescription.capacity.max.invalid"
      }

      if (description.capacity.desired < 0) {
        errors.rejectValue "capacity", "titusDeployDescription.capacity.desired.invalid"
      }
    }

    if (description.resources) {
      if (description.resources.cpu <= 0) {
        errors.rejectValue "resources.cpu", "titusDeployDescription.resources.cpu.invalid"
      }

      if (description.resources.memory <= 0) {
        errors.rejectValue "resources.memory", "titusDeployDescription.resources.memory.invalid"
      }

      if (description.resources.disk <= 0) {
        errors.rejectValue "resources.disk", "titusDeployDescription.resources.disk.invalid"
      }

    } else {
      errors.rejectValue "resources", "titusDeployDescription.resources.empty"
    }
  }
}
