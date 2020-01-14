package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteTencentSecurityGroupDescriptionValidator")
public class DeleteTencentSecurityGroupDescriptionValidator
    extends DescriptionValidator<DeleteTencentSecurityGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions, DeleteTencentSecurityGroupDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getSecurityGroupId())) {
      errors.rejectValue(
          "securityGroupId", "DeleteTencentSecurityGroupDescription.securityGroupId.empty");
    }

    if (StringUtils.isEmpty(description.getAccountName())) {
      errors.rejectValue("accountName", "DeleteTencentSecurityGroupDescription.accountName.empty");
    }

    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "DeleteTencentSecurityGroupDescription.region.empty");
    }
  }
}
