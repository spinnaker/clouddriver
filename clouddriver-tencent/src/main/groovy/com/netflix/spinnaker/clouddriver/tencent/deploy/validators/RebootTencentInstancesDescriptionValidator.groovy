package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.RebootTencentInstancesDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentInstancesDescriptionValidator")
class RebootTencentInstancesDescriptionValidator extends DescriptionValidator<RebootTencentInstancesDescription> {
  @Override
  void validate(List priorDescriptions, RebootTencentInstancesDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "RebootTencentInstancesDescription.region.empty"
    }

    if (!description.instanceIds) {
      errors.rejectValue "instanceIds", "RebootTencentInstancesDescription.instanceIds.empty"
    }
  }
}
