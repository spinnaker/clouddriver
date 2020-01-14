package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.RebootTencentInstancesDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentInstancesDescriptionValidator")
public class RebootTencentInstancesDescriptionValidator
    extends DescriptionValidator<RebootTencentInstancesDescription> {
  @Override
  public void validate(
      List priorDescriptions, RebootTencentInstancesDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "RebootTencentInstancesDescription.region.empty");
    }

    if (CollectionUtils.isEmpty(description.getInstanceIds())) {
      errors.rejectValue("instanceIds", "RebootTencentInstancesDescription.instanceIds.empty");
    }
  }
}
