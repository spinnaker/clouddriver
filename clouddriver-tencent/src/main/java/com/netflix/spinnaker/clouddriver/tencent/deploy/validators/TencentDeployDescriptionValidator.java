package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("tencentDeployDescriptionValidator")
public class TencentDeployDescriptionValidator
    extends DescriptionValidator<TencentDeployDescription> {
  @Override
  public void validate(
      List priorDescriptions, TencentDeployDescription description, Errors errors) {

    if (StringUtils.isEmpty(description.getApplication())) {
      errors.rejectValue("application", "tencentDeployDescription.application.empty");
    }

    if (StringUtils.isEmpty(description.getImageId())) {
      errors.rejectValue("imageId", "tencentDeployDescription.imageId.empty");
    }

    if (StringUtils.isEmpty(description.getInstanceType())) {
      errors.rejectValue("instanceType", "tencentDeployDescription.instanceType.empty");
    }

    if (CollectionUtils.isEmpty(description.getZones())
        && CollectionUtils.isEmpty(description.getSubnetIds())) {
      errors.rejectValue(
          "zones or subnetIds", "tencentDeployDescription.subnetIds.or.zones.not.supplied");
    }

    if (description.getMaxSize() == null) {
      errors.rejectValue("maxSize", "tencentDeployDescription.maxSize.empty");
    }

    if (description.getMinSize() == null) {
      errors.rejectValue("minSize", "tencentDeployDescription.minSize.empty");
    }

    if (description.getDesiredCapacity() == null) {
      errors.rejectValue("desiredCapacity", "tencentDeployDescription.desiredCapacity.empty");
    }
  }
}
