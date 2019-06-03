package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@TencentOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentSecurityGroupDescriptionValidator")
class UpsertTencentSecurityGroupDescriptionValidator extends DescriptionValidator<UpsertTencentSecurityGroupDescription> {

  @Override
  void validate(List priorDescriptions, UpsertTencentSecurityGroupDescription description, Errors errors) {
    log.info("Validate tencent security group description ${description}")
    if (!description.securityGroupName) {
      errors.rejectValue "securityGroupName", "UpsertTencentSecurityGroupDescription.securityGroupName.empty"
    }

    if (!description.accountName) {
      errors.rejectValue "accountName", "UpsertTencentSecurityGroupDescription.accountName.empty"
    }

    if (!description.region) {
      errors.rejectValue "region", "UpsertTencentSecurityGroupDescription.region.empty"
    }
  }
}
