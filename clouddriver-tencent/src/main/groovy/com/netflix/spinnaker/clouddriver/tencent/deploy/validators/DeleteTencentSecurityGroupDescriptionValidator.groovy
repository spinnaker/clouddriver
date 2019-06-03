package com.netflix.spinnaker.clouddriver.tencent.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@TencentOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteTencentSecurityGroupDescriptionValidator")
class DeleteTencentSecurityGroupDescriptionValidator extends DescriptionValidator<DeleteTencentSecurityGroupDescription> {

  @Override
  void validate(List priorDescriptions, DeleteTencentSecurityGroupDescription description, Errors errors) {
    if (!description.securityGroupId) {
      errors.rejectValue "securityGroupId", "DeleteTencentSecurityGroupDescription.securityGroupId.empty"
    }

    if (!description.accountName) {
      errors.rejectValue "accountName", "DeleteTencentSecurityGroupDescription.accountName.empty"
    }

    if (!description.region) {
      errors.rejectValue "region", "DeleteTencentSecurityGroupDescription.region.empty"
    }
  }
}
