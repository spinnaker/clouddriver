/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors

abstract class AbstractDcosDescriptionValidatorSupport<T extends AbstractDcosCredentialsDescription> extends DescriptionValidator<T> {

  private final AccountCredentialsProvider accountCredentialsProvider
  private final String descriptionName

  AbstractDcosDescriptionValidatorSupport(AccountCredentialsProvider accountCredentialsProvider, String descriptionName) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.descriptionName = descriptionName
  }

  @Override
  void validate(List priorDescriptions, T description, Errors errors) {
    if (!description.credentials) {
      errors.rejectValue "credentials", "${descriptionName}.credentials.empty"
    } else {
      def credentials = getAccountCredentials(description?.credentials?.name)
      if (!(credentials instanceof DcosCredentials)) {
        errors.rejectValue("credentials", "${descriptionName}.credentials.invalid")
      }
    }
  }

  AccountCredentials getAccountCredentials(String accountName) {
    accountCredentialsProvider.getCredentials(accountName)
  }
}
