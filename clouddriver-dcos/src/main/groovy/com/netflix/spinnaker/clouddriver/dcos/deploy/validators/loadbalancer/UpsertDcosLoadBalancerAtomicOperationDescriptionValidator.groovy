package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@DcosOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
class UpsertDcosLoadBalancerAtomicOperationDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<UpsertDcosLoadBalancerAtomicOperationDescription> {

  @Autowired
  UpsertDcosLoadBalancerAtomicOperationDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "upsertDcosLoadBalancerAtomicOperationDescription")
  }

  @Override
  void validate(List priorDescriptions, UpsertDcosLoadBalancerAtomicOperationDescription description, Errors errors) {
    super.validate(priorDescriptions, description, errors)

    // TODO Regex name validation for DC/OS apps
    // Will need to apply to group as well.
    if (!description.name || description.name.empty) {
      errors.rejectValue("name", "${descriptionName}.name.empty");
    } else if (!MarathonPathId.isPartValid(description.name)) {
      errors.rejectValue "name", "${descriptionName}.name.invalid"
    }

    if (description.cpus < 0) {
      errors.rejectValue "cpus", "${descriptionName}.cpus.invalid"
    }

    if (description.instances < 0) {
      errors.rejectValue "instances", "${descriptionName}.instances.invalid"
    }

    if (description.mem < 0) {
      errors.rejectValue "mem", "${descriptionName}.mem.invalid"
    }

    if (description.acceptedResourceRoles.stream().anyMatch({ r -> r == null || r.empty })) {
      errors.rejectValue("acceptedResourceRoles", "${descriptionName}.acceptedResourceRoles.invalid (Must not contain null or empty values)");
    }

    def portRange = description.portRange
    if (!portRange) {
      errors.rejectValue("portRange", "${descriptionName}.portRange.empty");
    } else {
      if (!portRange.protocol || portRange.protocol.empty) {
        errors.rejectValue("portRange", "${descriptionName}.portRange.protocol.invalid")
      }
      if (portRange.minPort < 10000) {
        errors.rejectValue("portRange", "${descriptionName}.portRange.minPort.invalid (minPort < 10000)")
      }
      if (portRange.minPort > portRange.maxPort) {
        errors.rejectValue("portRange", "${descriptionName}.portRange.invalid (maxPort < minPort)")
      }
    }
  }
}
