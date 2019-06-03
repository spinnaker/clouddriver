package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component("upsertTencentScalingPolicyDescriptionValidator")
public class UpsertTencentScalingPolicyDescriptionValidator
    extends DescriptionValidator<UpsertTencentScalingPolicyDescription> {
  @Override
  public void validate(
      List priorDescriptions, UpsertTencentScalingPolicyDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "upsertScalingPolicyDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue("serverGroupName", "upsertScalingPolicyDescription.serverGroupName.empty");
    }
  }
}
