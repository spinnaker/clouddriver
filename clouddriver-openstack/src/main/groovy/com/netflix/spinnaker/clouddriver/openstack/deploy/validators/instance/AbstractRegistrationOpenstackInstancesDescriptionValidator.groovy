/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

/**
 *
 */
class AbstractRegistrationOpenstackInstancesDescriptionValidator extends DescriptionValidator<OpenstackInstancesRegistrationDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, OpenstackInstancesRegistrationDescription description, Errors errors) {
    def validator = new OpenstackAttributeValidator("OpenstackInstancesRegistrationDescription", errors)
    validator.validateCredentials(description.account, accountCredentialsProvider)
    validator.validateNotEmpty(description.instanceIds, "instanceIds")
    validator.validateNotEmpty(description.loadBalancerIds, "loadBalancerIds")
  }

}
