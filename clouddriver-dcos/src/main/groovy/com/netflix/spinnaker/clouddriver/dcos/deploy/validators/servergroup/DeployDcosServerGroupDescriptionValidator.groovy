package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
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
      errors.rejectValue "region", "${descriptionName}.region.empty"
    } else if (MarathonPathId.validatePart(description.region.replaceAll(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR))) {
      errors.rejectValue "region", "${descriptionName}.region.invalid"
    }

    if (!description.application) {
      errors.rejectValue "application", "${descriptionName}.application.empty"
    } else if (MarathonPathId.validatePart(description.application)) {
      errors.rejectValue "application", "${descriptionName}.application.invalid"
    }

    if (description.stack && MarathonPathId.validatePart(description.stack)) {
      errors.rejectValue "stack", "${descriptionName}.stack.invalid"
    }

    if (description.freeFormDetails && MarathonPathId.validatePart(description.freeFormDetails)) {
      errors.rejectValue "freeFormDetails", "${descriptionName}.freeFormDetails.invalid"
    }

    if (!description.desiredCapacity || description.desiredCapacity <= 0) {
      errors.rejectValue "desiredCapacity", "${descriptionName}.desiredCapacity.invalid"
    }

    if (!description.cpus || description.cpus <= 0) {
      errors.rejectValue "cpus", "${descriptionName}.cpus.invalid"
    }

    if (!description.mem || description.mem <= 0) {
      errors.rejectValue "mem", "${descriptionName}.mem.invalid"
    }

    if (description.disk && description.disk < 0) {
      errors.rejectValue "disk", "${descriptionName}.disk.invalid"
    }

    if (description.gpus && description.gpus < 0) {
      errors.rejectValue "gpus", "${descriptionName}.gpus.invalid"
    }
  }
}
