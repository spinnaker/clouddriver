/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import mesosphere.dcos.client.model.DCOSAuthCredentials

class DcosCredentials implements AccountCredentials<DCOSAuthCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()
  final String registry
  final String dcosUrl
  final DCOSAuthCredentials dcosAuthCredentials

  DcosCredentials(String name,
                  String environment,
                  String accountType,
                  String dcosUrl,
                  String user,
                  String password) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.registry = registry
    this.dcosUrl = dcosUrl
    this.dcosAuthCredentials = DCOSAuthCredentials.forUserAccount(user, password)
  }

  @Override
  DCOSAuthCredentials getCredentials() {
    dcosAuthCredentials
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }
}
