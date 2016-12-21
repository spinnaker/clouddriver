/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.AppEngineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@AppEngineOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("disableAppEngineDescriptionValidator")
class DisableAppEngineDescriptionValidator extends DescriptionValidator<EnableDisableAppEngineDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AppEngineLoadBalancerProvider appEngineLoadBalancerProvider

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  @Override
  void validate(List priorDescriptions, EnableDisableAppEngineDescription description, Errors errors) {
    def helper = new StandardAppEngineAttributeValidator("disableAppEngineAtomicOperationDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateNotEmpty(description.serverGroupName, "serverGroupName")
    helper.validateServerGroupCanBeDisabled(description.serverGroupName,
                                            description.credentials,
                                            appEngineClusterProvider,
                                            appEngineLoadBalancerProvider,
                                            "serverGroupName")
  }
}
