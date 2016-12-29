/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.AbstractRegistrationDcosAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.StandardDcosAttributeValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@DcosOperation(AtomicOperations.DEREGISTER_INSTANCES_FROM_LOAD_BALANCER)
@Component
class DeregisterDcosAtomicOperationValidator extends DescriptionValidator<AbstractRegistrationDcosAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, AbstractRegistrationDcosAtomicOperationDescription description, Errors errors) {
    def helper = new StandardDcosAttributeValidator("deregisterDcosAtomicOperationDescription", errors)

    AbstractRegistrationDcosAtomicOperationValidator.validate(description, helper, accountCredentialsProvider)
  }
}
