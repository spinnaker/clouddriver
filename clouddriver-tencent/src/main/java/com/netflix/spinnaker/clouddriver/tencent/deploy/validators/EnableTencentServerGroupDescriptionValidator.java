package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableTencentServerGroupDescriptionValidator")
public class EnableTencentServerGroupDescriptionValidator
    extends DescriptionValidator<EnableDisableTencentServerGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions,
      EnableDisableTencentServerGroupDescription description,
      Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "enableTencentServerGroupDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue(
          "serverGroupName", "enableTencentServerGroupDescription.serverGroupName.empty");
    }
  }
}
