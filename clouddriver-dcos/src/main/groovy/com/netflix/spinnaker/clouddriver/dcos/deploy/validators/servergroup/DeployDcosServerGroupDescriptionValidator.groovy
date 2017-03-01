package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
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

    if (!description.region || description.region.empty) {
      errors.rejectValue "region", "deployDcosServerGroupDescription.region.empty"
    }

    if (!description.application) {
      errors.rejectValue "application", "deployDcosServerGroupDescription.application.empty"
    }

    if (!description.desiredCapacity || description.desiredCapacity <= 0) {
      errors.rejectValue "desiredCapacity", "deployDcosServerGroupDescription.desiredCapacity.invalid"
    }

    if (!description.cpus || description.cpus <= 0) {
      errors.rejectValue "cpus", "deployDcosServerGroupDescription.cpus.invalid"
    }

    if (!description.mem || description.mem <= 0) {
      errors.rejectValue "mem", "deployDcosServerGroupDescription.mem.invalid"
    }

    if (description.disk && description.disk < 0) {
      errors.rejectValue "disk", "deployDcosServerGroupDescription.disk.invalid"
    }

    if (description.gpus && description.gpus < 0) {
      errors.rejectValue "gpus", "deployDcosServerGroupDescription.gpus.invalid"
    }
  }
}
