package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentSecurityGroupDescriptionValidator")
public class UpsertTencentSecurityGroupDescriptionValidator
    extends DescriptionValidator<UpsertTencentSecurityGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions,
      final UpsertTencentSecurityGroupDescription description,
      Errors errors) {
    if (StringUtils.isEmpty(description.getSecurityGroupName())) {
      errors.rejectValue(
          "securityGroupName", "UpsertTencentSecurityGroupDescription.securityGroupName.empty");
    }

    if (StringUtils.isEmpty(description.getAccountName())) {
      errors.rejectValue("accountName", "UpsertTencentSecurityGroupDescription.accountName.empty");
    }

    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "UpsertTencentSecurityGroupDescription.region.empty");
    }
  }
}
