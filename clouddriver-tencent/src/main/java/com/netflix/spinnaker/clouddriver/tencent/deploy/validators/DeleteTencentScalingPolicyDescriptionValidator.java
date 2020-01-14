package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScalingPolicyDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.DELETE_SCALING_POLICY)
@Component("deleteTencentScalingPolicyDescriptionValidator")
public class DeleteTencentScalingPolicyDescriptionValidator
    extends DescriptionValidator<DeleteTencentScalingPolicyDescription> {
  @Override
  public void validate(
      List priorDescriptions, DeleteTencentScalingPolicyDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "deleteScalingPolicyDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue("serverGroupName", "deleteScalingPolicyDescription.serverGroupName.empty");
    }

    if (StringUtils.isEmpty(description.getScalingPolicyId())) {
      errors.rejectValue("scalingPolicyId", "deleteScalingPolicyDescription.scalingPolicyId.empty");
    }
  }
}
