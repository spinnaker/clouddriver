package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.RESIZE_SERVER_GROUP)
class ResizeDcosServerGroupDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<ResizeDcosServerGroupDescription> {

  @Autowired
  ResizeDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "resizeDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, ResizeDcosServerGroupDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "resizeDcosServerGroupDescription.serverGroupName.empty"
    }

    if (!description.desired || description.desired <= 0) {
      errors.rejectValue "desired", "resizeDcosServerGroupDescription.desired.invalid"
    }
  }
}
