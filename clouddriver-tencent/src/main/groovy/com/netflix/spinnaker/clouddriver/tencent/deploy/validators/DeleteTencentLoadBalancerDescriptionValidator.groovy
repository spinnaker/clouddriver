package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentLoadBalancerDescriptionValidator")
class DeleteTencentLoadBalancerDescriptionValidator extends DescriptionValidator<DeleteTencentLoadBalancerDescription> {
  @Override
  void validate(List priorDescriptions, DeleteTencentLoadBalancerDescription description, Errors errors) {

    if (!description.application) {
      errors.rejectValue "application", "DeleteTencentLoadBalancerDescription.application.empty"
    }

    if (!description.accountName) {
      errors.rejectValue "accountName", "DeleteTencentLoadBalancerDescription.accountName.empty"
    }

    if (!description.region) {
      errors.rejectValue "region", "UpsertTencentLoadBalancerDescription.region.empty"
    }

    if (!description.loadBalancerId) {
      errors.rejectValue "loadBalancerId", "DeleteTencentLoadBalancerDescription.loadBalancerId.empty"
    }
  }
}
