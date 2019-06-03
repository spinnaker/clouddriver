package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("tencentDeployDescriptionValidator")
class TencentDeployDescriptionValidator extends DescriptionValidator<TencentDeployDescription> {
  @Override
  void validate(List priorDescriptions, TencentDeployDescription description, Errors errors) {

    if (!description.application) {
      errors.rejectValue "application", "tencentDeployDescription.application.empty"
    }

    if (!description.imageId) {
      errors.rejectValue "imageId", "tencentDeployDescription.imageId.empty"
    }

    if (!description.instanceType) {
      errors.rejectValue "instanceType", "tencentDeployDescription.instanceType.empty"
    }

    if (!description.zones && !description.subnetIds) {
      errors.rejectValue "zones or subnetIds", "tencentDeployDescription.subnetIds.or.zones.not.supplied"
    }

    if (description.maxSize == null) {
      errors.rejectValue "maxSize", "tencentDeployDescription.maxSize.empty"
    }

    if (description.minSize == null) {
      errors.rejectValue "minSize", "tencentDeployDescription.minSize.empty"
    }

    if (description.desiredCapacity == null) {
      errors.rejectValue "desiredCapacity", "tencentDeployDescription.desiredCapacity.empty"
    }

  }
}
