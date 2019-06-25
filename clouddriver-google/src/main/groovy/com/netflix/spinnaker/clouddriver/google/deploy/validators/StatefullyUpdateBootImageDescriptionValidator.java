package com.netflix.spinnaker.clouddriver.google.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.google.deploy.description.StatefullyUpdateBootImageDescription;
import java.util.List;
import org.springframework.validation.Errors;

public class StatefullyUpdateBootImageDescriptionValidator
    extends DescriptionValidator<StatefullyUpdateBootImageDescription> {

  @Override
  public void validate(
      List priorDescriptions, StatefullyUpdateBootImageDescription description, Errors errors) {
    StandardGceAttributeValidator helper =
        new StandardGceAttributeValidator("statefullyUpdateBootImageDescription", errors);
    helper.validateRegion(description.getRegion(), description.getCredentials());
    helper.validateServerGroupName(description.getServerGroupName());
    helper.validateName(description.getBootImage(), "bootImage");
  }
}
