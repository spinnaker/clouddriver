package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.DISABLE_SERVER_GROUP)
class DisableDcosServerGroupDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DisableDcosServerGroupDescription> {

  @Autowired
  DisableDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "disableDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DisableDcosServerGroupDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.region || description.region.empty) {
      errors.rejectValue "region", "${descriptionName}.region.empty"
    } else if (MarathonPathId.validatePart(description.region.replaceAll(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR))) {
      errors.rejectValue "region", "${descriptionName}.region.invalid"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.empty"
    } else if (MarathonPathId.validatePart(description.serverGroupName)) {
      errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.invalid"
    }
  }
}
