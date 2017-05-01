package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.job

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.RUN_JOB)
class RunDcosJobDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<RunDcosJobDescription> {

  @Autowired
  RunDcosJobDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "runDcosJobDescription")
  }

  @Override
  void validate(List priorDescriptions, RunDcosJobDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)
    if (!description.general?.id) {
      errors.rejectValue "general.id", "${descriptionName}.general.id.empty"
    } else if (!MarathonPathId.isPartValid(description.general?.id)) {
      errors.rejectValue "general.id", "${descriptionName}.general.id.invalid"
    }
  }
}
