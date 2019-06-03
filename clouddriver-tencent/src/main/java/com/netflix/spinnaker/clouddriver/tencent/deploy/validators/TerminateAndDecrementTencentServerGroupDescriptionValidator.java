package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateAndDecrementTencentServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component("terminateAndDecrementTencentServerGroupDescriptionValidator")
public class TerminateAndDecrementTencentServerGroupDescriptionValidator
    extends DescriptionValidator<TerminateAndDecrementTencentServerGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions,
      TerminateAndDecrementTencentServerGroupDescription description,
      Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue(
          "region", "TerminateAndDecrementTencentServerGroupDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getInstance())) {
      errors.rejectValue(
          "instance", "TerminateAndDecrementTencentServerGroupDescription.instance.empty");
    }
  }
}
