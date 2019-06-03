package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@Component("deleteTencentScheduledActionDescriptionValidator")
public class DeleteTencentScheduledActionDescriptionValidator
    extends DescriptionValidator<DeleteTencentScheduledActionDescription> {
  @Override
  public void validate(
      List priorDescriptions, DeleteTencentScheduledActionDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "deleteScheduledActionDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue(
          "serverGroupName", "deleteScheduledActionDescription.serverGroupName.empty");
    }

    if (StringUtils.isEmpty(description.getScheduledActionId())) {
      errors.rejectValue(
          "scheduledActionId", "deleteScheduledActionDescription.scalingPolicyId.empty");
    }
  }
}
