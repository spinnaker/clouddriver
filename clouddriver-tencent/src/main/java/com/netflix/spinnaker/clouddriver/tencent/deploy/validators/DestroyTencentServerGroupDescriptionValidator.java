package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyTencentServerGroupDescriptionValidator")
public class DestroyTencentServerGroupDescriptionValidator
    extends DescriptionValidator<DestroyTencentServerGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions, DestroyTencentServerGroupDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "tencentDestroyServerGroupDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue(
          "serverGroupName", "tencentDestroyServerGroupDescription.serverGroupName.empty");
    }
  }
}
