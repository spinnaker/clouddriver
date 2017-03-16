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

    if (description.appId) {
      if (!description.hostId && !description.taskIds) {
        errors.rejectValue "hostId|taskIds", "${descriptionName}.hostId|taskIds.empty"
      }

      if (description.hostId && description.taskIds) {
        errors.rejectValue "hostId|taskIds", "${descriptionName}.hostId|taskIds.invalid"
      }

      if (description.taskIds && description.taskIds.size() != 1) {
        errors.rejectValue "taskIds", "${descriptionName}.taskIds.invalid"
      }

      if (!DcosSpinnakerAppId.parse(description.appId, description.credentials.name).isPresent()) {
        errors.rejectValue "appId", "${descriptionName}.appId.invalid"
      }
    } else {
      if (!description.taskIds) {
        errors.rejectValue "taskIds", "${descriptionName}.taskIds.empty"
      }
    }
  }
}
