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

package com.netflix.spinnaker.clouddriver.security

import com.netflix.spinnaker.security.AuthenticatedRequest

class DefaultAccountCredentialsProvider implements AccountCredentialsProvider {
  final AccountCredentialsRepository repository

  DefaultAccountCredentialsProvider() {
    this(new MapBackedAccountCredentialsRepository())
  }

  DefaultAccountCredentialsProvider(AccountCredentialsRepository repository) {
    this.repository = repository
  }

  @Override
  Set<? extends AccountCredentials> getAll() {
    def accountCredentials = repository.getAll()
    def allowedAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(",") as List<String>

    return accountCredentials.findAll {
      shouldAllowAccess(it, allowedAccounts)
    }
  }

  @Override
  AccountCredentials getCredentials(String name) {
    def accountCredentials = repository.getOne(name)
    def allowedAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(",") as List<String>

    return shouldAllowAccess(accountCredentials, allowedAccounts) ? accountCredentials : null
  }

  static boolean shouldAllowAccess(AccountCredentials credentials, List<String> allowedAccounts) {
    if (!credentials) {
      return false
    }
    def isAnonymousCredential = !credentials.requiredGroupMembership
    def isInRequiredGroup = credentials.requiredGroupMembership?.intersect(allowedAccounts)

    return isAnonymousCredential || isInRequiredGroup
  }
}
