package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.DISABLE_SERVER_GROUP)
class DisableDcosServerGroupDescriptionValidator extends AbstractDcosServerGroupValidator<DisableDcosServerGroupDescription> {

  @Autowired
  DisableDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "disableDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DisableDcosServerGroupDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)
  }
}
