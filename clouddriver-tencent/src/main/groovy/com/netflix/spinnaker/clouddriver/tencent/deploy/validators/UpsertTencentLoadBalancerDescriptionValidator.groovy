package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@TencentOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertTencentLoadBalancerDescriptionValidator")
class UpsertTencentLoadBalancerDescriptionValidator extends DescriptionValidator<UpsertTencentLoadBalancerDescription> {
  @Override
  void validate(List priorDescriptions, UpsertTencentLoadBalancerDescription description, Errors errors) {
    log.info("Enter tencent validate ${description.properties}")
    if (!description.application) {
      errors.rejectValue "application", "UpsertTencentLoadBalancerDescription.application.empty"
    }

    if (!description.accountName) {
      errors.rejectValue "accountName", "UpsertTencentLoadBalancerDescription.accountName.empty"
    }

    if (!description.loadBalancerName) {
      errors.rejectValue "loadBalancerName", "UpsertTencentLoadBalancerDescription.loadBalancerName.empty"
    }

    if (!description.region) {
      errors.rejectValue "region", "UpsertTencentLoadBalancerDescription.region.empty"
    }

    if (!description.loadBalancerType) {
      errors.rejectValue "loadBalancerType", "UpsertTencentLoadBalancerDescription.loadBalancerType.empty"
      //OPEN check
    }

    //listener check

    //rule check
  }
}
