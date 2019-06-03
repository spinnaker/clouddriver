package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScalingPolicyDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.DELETE_SCALING_POLICY)
@Component("deleteTencentScalingPolicyDescriptionValidator")
class DeleteTencentScalingPolicyDescriptionValidator extends DescriptionValidator<DeleteTencentScalingPolicyDescription> {
  @Override
  void validate(List priorDescriptions, DeleteTencentScalingPolicyDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "deleteScalingPolicyDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "deleteScalingPolicyDescription.serverGroupName.empty"
    }

    if (!description.scalingPolicyId) {
      errors.rejectValue "scalingPolicyId", "deleteScalingPolicyDescription.scalingPolicyId.empty"
    }
  }
}
