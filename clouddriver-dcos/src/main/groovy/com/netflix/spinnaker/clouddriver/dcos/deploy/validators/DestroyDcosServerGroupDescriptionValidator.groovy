package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.DESTROY_SERVER_GROUP)
class DestroyDcosServerGroupDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DestroyDcosServerGroupDescription> {

  @Autowired
  DestroyDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "destroyDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DestroyDcosServerGroupDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "destroyDcosServerGroupDescription.serverGroupName.empty"
    }
  }
}
