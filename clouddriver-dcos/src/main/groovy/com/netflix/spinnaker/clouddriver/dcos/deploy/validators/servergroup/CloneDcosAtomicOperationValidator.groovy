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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.CloneDcosAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.DcosContainerValidator
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.StandardDcosAttributeValidator
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@DcosOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneDcosAtomicOperationValidator extends DescriptionValidator<CloneDcosAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CloneDcosAtomicOperationDescription description, Errors errors) {
    def helper = new StandardDcosAttributeValidator("cloneDcosAtomicOperationDescription", errors)
    if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    DcosCredentials credentials = (DcosCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

    helper.validateServerGroupCloneSource(description.source, "source")
    if (description.application) {
      helper.validateApplication(description.application, "application")
    }

    if (description.stack) {
      helper.validateStack(description.stack, "stack")
    }

    if (description.freeFormDetails) {
      helper.validateDetails(description.freeFormDetails, "details")
    }

    if (description.targetSize != null) {
      helper.validateNonNegative(description.targetSize, "targetSize")
    }

    if (description.namespace) {
      helper.validateNamespace(credentials, description.namespace, "namespace")
    }

    if (description.restartPolicy) {
      helper.validateRestartPolicy(description.restartPolicy, "restartPolicy")
    }

    if (description.loadBalancers) {
      description.loadBalancers.eachWithIndex { name, idx ->
        helper.validateName(name, "loadBalancers[${idx}]")
      }
    }

    if (description.securityGroups) {
      description.securityGroups.eachWithIndex { name, idx ->
        helper.validateName(name, "securityGroups[${idx}]")
      }
    }

    if (description.containers) {
      description.containers.eachWithIndex { container, idx ->
        DcosContainerValidator.validate(container, helper, "container[${idx}]")
      }
    }
  }
}
