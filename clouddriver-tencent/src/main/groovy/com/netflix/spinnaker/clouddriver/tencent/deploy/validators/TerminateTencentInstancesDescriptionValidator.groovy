package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentInstancesDescriptionValidator")
class TerminateTencentInstancesDescriptionValidator extends DescriptionValidator<TerminateTencentInstancesDescription> {
  @Override
  void validate(List priorDescriptions, TerminateTencentInstancesDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "TerminateTencentInstancesDescription.region.empty"
    }

    if (!description.instanceIds) {
      errors.rejectValue "instanceIds", "TerminateTencentInstancesDescription.instanceIds.empty"
    }
  }
}
