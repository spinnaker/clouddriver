package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("upsertTencentScheduledActionsDescriptionValidator")
class UpsertTencentScheduledActionsDescriptionValidator extends DescriptionValidator<UpsertTencentScheduledActionDescription> {
  @Override
  void validate(List priorDescriptions, UpsertTencentScheduledActionDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "upsertScheduledActionDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "upsertScheduledActionDescription.serverGroupName.empty"
    }
  }
}
