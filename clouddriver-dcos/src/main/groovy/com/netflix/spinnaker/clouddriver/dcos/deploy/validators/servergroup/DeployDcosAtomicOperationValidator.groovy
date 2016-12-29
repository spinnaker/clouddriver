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
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.DcosContainerValidator
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.DcosVolumeSourceValidator
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.StandardDcosAttributeValidator
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@DcosOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("deployDcosAtomicOperationValidator")
class DeployDcosAtomicOperationValidator extends DescriptionValidator<DeployDcosAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DeployDcosAtomicOperationDescription description, Errors errors) {
    def helper = new StandardDcosAttributeValidator("deployDcosAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    DcosCredentials credentials = (DcosCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

    helper.validateApplication(description.application, "application")
    helper.validateStack(description.stack, "stack")
    helper.validateDetails(description.freeFormDetails, "details")
    helper.validateNonNegative(description.targetSize, "targetSize")
    helper.validateNamespace(credentials, description.namespace, "namespace")
    helper.validateRestartPolicy(description.restartPolicy, "restartPolicy")

    description.volumeSources.eachWithIndex { source, idx ->
      DcosVolumeSourceValidator.validate(source, helper, "volumeSources[${idx}]")
    }

    description.loadBalancers.eachWithIndex { name, idx ->
      helper.validateName(name, "loadBalancers[${idx}]")
    }

    description.securityGroups.eachWithIndex { name, idx ->
      helper.validateName(name, "securityGroups[${idx}]")
    }

    helper.validateNotEmpty(description.containers, "containers")
    description.containers.eachWithIndex { container, idx ->
      DcosContainerValidator.validate(container, helper, "container[${idx}]")
    }

    if (description.scalingPolicy) {
      helper.validateNotEmpty(description.scalingPolicy.cpuUtilization, "scalingPolicy.cpuUtilization")
      helper.validatePositive(description.scalingPolicy.cpuUtilization.target, "scalingPolicy.cpuUtilization.target")
    }
  }
}
