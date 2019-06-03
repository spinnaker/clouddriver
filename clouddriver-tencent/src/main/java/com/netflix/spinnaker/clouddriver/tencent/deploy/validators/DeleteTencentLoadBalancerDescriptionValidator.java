package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentLoadBalancerDescriptionValidator")
public class DeleteTencentLoadBalancerDescriptionValidator
    extends DescriptionValidator<DeleteTencentLoadBalancerDescription> {
  @Override
  public void validate(
      List priorDescriptions, DeleteTencentLoadBalancerDescription description, Errors errors) {

    if (StringUtils.isEmpty(description.getApplication())) {
      errors.rejectValue("application", "DeleteTencentLoadBalancerDescription.application.empty");
    }

    if (StringUtils.isEmpty(description.getAccountName())) {
      errors.rejectValue("accountName", "DeleteTencentLoadBalancerDescription.accountName.empty");
    }

    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "UpsertTencentLoadBalancerDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getLoadBalancerId())) {
      errors.rejectValue(
          "loadBalancerId", "DeleteTencentLoadBalancerDescription.loadBalancerId.empty");
    }
  }
}
