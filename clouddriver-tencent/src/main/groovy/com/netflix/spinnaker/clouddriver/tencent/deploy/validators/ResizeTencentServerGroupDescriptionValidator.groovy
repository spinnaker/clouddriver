package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeTencentServerGroupDescriptionValidator")
class ResizeTencentServerGroupDescriptionValidator extends DescriptionValidator<ResizeTencentServerGroupDescription> {
  @Override
  void validate(
    List priorDescriptions, ResizeTencentServerGroupDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "ResizeTencentServerGroupDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "ResizeTencentServerGroupDescription.serverGroupName.empty"
    }

    if (description.capacity == null) {
      errors.rejectValue "capacity", "ResizeTencentServerGroupDescription.capacity.empty"
    }
  }
}
