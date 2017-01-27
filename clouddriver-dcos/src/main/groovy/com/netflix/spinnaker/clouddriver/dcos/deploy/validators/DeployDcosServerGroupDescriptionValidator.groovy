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
      errors.rejectValue "application", "deployDcosServerGroupDescription.application.empty"
    }

    if (!description.instances || description.instances <= 0) {
      errors.rejectValue "instances", "deployDcosServerGroupDescription.instances.invalid"
    }

    if (!description.cpus || description.cpus <= 0) {
      errors.rejectValue "cpus", "deployDcosServerGroupDescription.cpus.invalid"
    }

    if (!description.mem || description.mem <= 0) {
      errors.rejectValue "mem", "deployDcosServerGroupDescription.mem.invalid"
    }

    if (description.disk == null || description.disk < 0) {
      errors.rejectValue "disk", "deployDcosServerGroupDescription.disk.invalid"
    }

    if (description.gpus == null || description.gpus < 0) {
      errors.rejectValue "gpus", "deployDcosServerGroupDescription.gpus.invalid"
    }

    if (!description.container) {
      errors.rejectValue "container", "deployDcosServerGroupDescription.container.empty"
    }
  }
}
