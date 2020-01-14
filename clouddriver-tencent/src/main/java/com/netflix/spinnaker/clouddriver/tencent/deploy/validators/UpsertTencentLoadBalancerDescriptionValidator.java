package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertTencentLoadBalancerDescriptionValidator")
public class UpsertTencentLoadBalancerDescriptionValidator
    extends DescriptionValidator<UpsertTencentLoadBalancerDescription> {
  @Override
  public void validate(
      List priorDescriptions,
      final UpsertTencentLoadBalancerDescription description,
      Errors errors) {
    if (StringUtils.isEmpty(description.getApplication())) {
      errors.rejectValue("application", "UpsertTencentLoadBalancerDescription.application.empty");
    }

    if (StringUtils.isEmpty(description.getAccountName())) {
      errors.rejectValue("accountName", "UpsertTencentLoadBalancerDescription.accountName.empty");
    }

    if (StringUtils.isEmpty(description.getLoadBalancerName())) {
      errors.rejectValue(
          "loadBalancerName", "UpsertTencentLoadBalancerDescription.loadBalancerName.empty");
    }

    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "UpsertTencentLoadBalancerDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getLoadBalancerType())) {
      errors.rejectValue(
          "loadBalancerType", "UpsertTencentLoadBalancerDescription.loadBalancerType.empty");
      // OPEN check
    }

    // listener check

    // rule check
  }
}
