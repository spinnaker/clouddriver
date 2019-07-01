/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
