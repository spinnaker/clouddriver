package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableTencentServerGroupDescriptionValidator")
class EnableTencentServerGroupDescriptionValidator extends DescriptionValidator<EnableDisableTencentServerGroupDescription> {
  @Override
  void validate(List priorDescriptions, EnableDisableTencentServerGroupDescription description, Errors errors) {
    if (!description.region) {
      errors.rejectValue "region", "enableTencentServerGroupDescription.region.empty"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "enableTencentServerGroupDescription.serverGroupName.empty"
    }
  }
}
