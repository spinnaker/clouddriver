/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
class TerminateDcosInstanceAndDecrementDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<TerminateDcosInstancesAndDecrementDescription> {

  @Autowired
  TerminateDcosInstanceAndDecrementDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "terminateDcosInstancesAndDecrementDescription")
  }

  @Override
  void validate(List priorDescriptions, TerminateDcosInstancesAndDecrementDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)
    if (!description.instanceIds) {
      errors.rejectValue "instanceIds", "${descriptionName}.instanceIds.empty"
    }
  }
}
