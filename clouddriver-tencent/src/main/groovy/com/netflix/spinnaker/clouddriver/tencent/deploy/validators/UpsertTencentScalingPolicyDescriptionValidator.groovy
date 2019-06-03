package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component("upsertTencentScalingPolicyDescriptionValidator")
class UpsertTencentScalingPolicyDescriptionValidator extends DescriptionValidator<UpsertTencentScalingPolicyDescription> {

  @Override
  void validate(List priorDescriptions, UpsertTencentScalingPolicyDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "upsertScalingPolicyDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "upsertScalingPolicyDescription.serverGroupName.empty"
    }
  }
}
