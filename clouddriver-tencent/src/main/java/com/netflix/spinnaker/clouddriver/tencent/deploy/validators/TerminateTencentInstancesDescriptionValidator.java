package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentInstancesDescriptionValidator")
public class TerminateTencentInstancesDescriptionValidator
    extends DescriptionValidator<TerminateTencentInstancesDescription> {
  @Override
  public void validate(
      List priorDescriptions, TerminateTencentInstancesDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "TerminateTencentInstancesDescription.region.empty");
    }

    if (CollectionUtils.isEmpty(description.getInstanceIds())) {
      errors.rejectValue("instanceIds", "TerminateTencentInstancesDescription.instanceIds.empty");
    }
  }
}
