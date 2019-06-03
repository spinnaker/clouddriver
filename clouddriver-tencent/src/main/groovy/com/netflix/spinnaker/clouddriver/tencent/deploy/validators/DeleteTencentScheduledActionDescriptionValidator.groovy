package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("deleteTencentScheduledActionDescriptionValidator")
class DeleteTencentScheduledActionDescriptionValidator extends DescriptionValidator<DeleteTencentScheduledActionDescription> {
  @Override
  void validate(List priorDescriptions, DeleteTencentScheduledActionDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "deleteScheduledActionDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "deleteScheduledActionDescription.serverGroupName.empty"
    }

    if (!description.scheduledActionId) {
      errors.rejectValue "scheduledActionId", "deleteScheduledActionDescription.scalingPolicyId.empty"
    }
  }
}
