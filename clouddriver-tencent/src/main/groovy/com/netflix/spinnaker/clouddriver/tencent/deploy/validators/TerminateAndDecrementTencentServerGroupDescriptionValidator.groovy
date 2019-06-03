package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateAndDecrementTencentServerGroupDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors


@TencentOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component("terminateAndDecrementTencentServerGroupDescriptionValidator")
class TerminateAndDecrementTencentServerGroupDescriptionValidator extends DescriptionValidator<TerminateAndDecrementTencentServerGroupDescription> {
  @Override
  void validate(List priorDescriptions, TerminateAndDecrementTencentServerGroupDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "TerminateAndDecrementTencentServerGroupDescription.region.empty"
    }

    if (!description.instance) {
      errors.rejectValue "instance", "TerminateAndDecrementTencentServerGroupDescription.instance.empty"
    }
  }
}
