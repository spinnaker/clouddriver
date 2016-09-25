/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.discovery.converters.Auto
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
class DefaultAllowedAccountsValidator implements AllowedAccountsValidator {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors) {
    if (!accountCredentialsProvider.all.find { it.requiredGroupMembership }) {
      // no accounts have group restrictions so no need to validate / log
      return
    }

    /*
     * Access should be allowed iff
     * - the account is not restricted (has no requiredGroupMembership)
     * - the user has been granted specific access (has the target account in its set of allowed accounts)
     */
    if (description.hasProperty("credentials")) {
      if (description.credentials instanceof Collection) {
        description.credentials.each { AccountCredentials credentials ->
          validateTargetAccount(credentials, allowedAccounts, description, user, errors)
        }
      } else {
        validateTargetAccount(description.credentials, allowedAccounts, description, user, errors)
      }
    } else {
      errors.rejectValue("credentials", "missing", "no credentials found in description: ${description.class.simpleName})")
    }
  }

  private void validateTargetAccount(AccountCredentials credentials, Collection<String> allowedAccounts, Object description, String user, Errors errors) {
    def requiredGroups = credentials.requiredGroupMembership*.toLowerCase()
    def targetAccount = credentials.name
    def isAuthorized = !requiredGroups || allowedAccounts.find { it.equalsIgnoreCase(targetAccount) }
    def json = null
    try {
      json = OBJECT_MAPPER.writeValueAsString(description)
    } catch (Exception ignored) {
    }
    def message = "${user} is ${isAuthorized ? '' : 'not '}authorized (account: ${targetAccount}, description: ${description.class.simpleName}, allowedAccounts: ${allowedAccounts}, requiredGroups: ${requiredGroups}, json: ${json})"
    if (!isAuthorized) {
      log.warn(message)
      errors.rejectValue("credentials", "unauthorized", "${user} is not authorized (account: ${targetAccount}, description: ${description.class.simpleName})")
    } else {
      log.info(message)
    }
  }
}
