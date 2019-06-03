package com.netflix.spinnaker.clouddriver.tencent.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

@TencentOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeTencentServerGroupDescriptionValidator")
public class ResizeTencentServerGroupDescriptionValidator
    extends DescriptionValidator<ResizeTencentServerGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions, ResizeTencentServerGroupDescription description, Errors errors) {
    if (StringUtils.isEmpty(description.getRegion())) {
      errors.rejectValue("region", "ResizeTencentServerGroupDescription.region.empty");
    }

    if (StringUtils.isEmpty(description.getServerGroupName())) {
      errors.rejectValue(
          "serverGroupName", "ResizeTencentServerGroupDescription.serverGroupName.empty");
    }

    if (description.getCapacity() == null) {
      errors.rejectValue("capacity", "ResizeTencentServerGroupDescription.capacity.empty");
    }
  }
}
