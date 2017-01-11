package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.DELETE_LOAD_BALANCER)
class DeleteDcosLoadBalancerAtomicOperationDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DeleteDcosLoadBalancerAtomicOperationDescription> {

  @Autowired
  DeleteDcosLoadBalancerAtomicOperationDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "deleteDcosLoadBalancerAtomicOperationDescription")
  }

  @Override
  void validate(List priorDescriptions, DeleteDcosLoadBalancerAtomicOperationDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    // TODO Regex name validation for DC/OS apps
    // Will need to apply to group as well.
    if (!description.loadBalancerName || description.loadBalancerName.empty) {
      errors.rejectValue("loadBalancerName", "${descriptionName}.loadBalancerName.empty");
    }
  }
}
