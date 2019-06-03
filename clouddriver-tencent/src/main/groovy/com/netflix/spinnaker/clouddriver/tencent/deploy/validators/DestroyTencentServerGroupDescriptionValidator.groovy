package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyTencentServerGroupDescriptionValidator")
class DestroyTencentServerGroupDescriptionValidator extends DescriptionValidator<DestroyTencentServerGroupDescription> {
  @Override
  void validate(List priorDescriptions, DestroyTencentServerGroupDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "tencentDestroyServerGroupDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "tencentDestroyServerGroupDescription.serverGroupName.empty"
    }
  }
}
