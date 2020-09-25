/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators;

import com.netflix.spinnaker.clouddriver.appengine.AppengineOperation;
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineConfigDescription;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AppengineOperation(AtomicOperations.DEPLOY_APPENGINE_CONFIG)
@Component("deployAppengineConfigDescriptionValidator")
public class DeployAppengineConfigDescriptionValidator
    extends DescriptionValidator<DeployAppengineConfigDescription> {

  @Autowired private AccountCredentialsProvider accountCredentialsProvider;

  @Override
  public void validate(
      List<DeployAppengineConfigDescription> priorDescriptions,
      DeployAppengineConfigDescription description,
      ValidationErrors errors) {
    StandardAppengineAttributeValidator helper =
        new StandardAppengineAttributeValidator(
            "deployAppengineConfigAtomicOperationDescription", errors);
    helper.validateCredentials(description.getAccountName(), accountCredentialsProvider);
  }
}
