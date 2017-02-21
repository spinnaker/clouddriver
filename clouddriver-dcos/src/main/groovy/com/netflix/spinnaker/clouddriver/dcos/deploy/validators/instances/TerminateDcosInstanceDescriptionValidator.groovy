package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instances

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instances.TerminateDcosInstancesDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.TERMINATE_INSTANCES)
class TerminateDcosInstanceDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<TerminateDcosInstancesDescription> {

  @Autowired
  TerminateDcosInstanceDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "terminateDcosInstancesDescription")
  }

  @Override
  void validate(List priorDescriptions, TerminateDcosInstancesDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    if (description.appId) {
      if (!description.hostId && !description.taskIds) {
        errors.rejectValue "hostId|taskIds", "terminateDcosInstancesDescription.hostId|taskIds.empty"
      }

      if (description.hostId && description.taskIds) {
        errors.rejectValue "hostId|taskIds", "terminateDcosInstancesDescription.hostId|taskIds.invalid"
      }

      if (description.taskIds && description.taskIds.size() != 1) {
        errors.rejectValue "taskIds", "terminateDcosInstancesDescription.taskIds.invalid"
      }
    } else {
      if (!description.taskIds) {
        errors.rejectValue "taskIds", "terminateDcosInstancesDescription.taskIds.empty"
      }
    }
  }
}
