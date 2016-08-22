/*
 * Copyright 2016 Veritas Technologies LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.openstack.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.Canonical
import groovy.transform.ToString

@ToString(includeNames = true, excludes = "password")
class OpenstackNamedAccountCredentials implements AccountCredentials<OpenstackCredentials> {
  static final String CLOUD_PROVIDER = "openstack"
  final String name
  final String environment
  final String accountType
  final String master
  final String username
  @JsonIgnore
  final String password
  final String tenantName
  final String domainName
  final String endpoint
  final List<String> requiredGroupMembership
  final OpenstackCredentials credentials
  List<String> regions
  final Boolean insecure
  final String heatTemplateLocation
  final LbaasConfig lbaasConfig

  OpenstackNamedAccountCredentials(String accountName,
                                   String environment,
                                   String accountType,
                                   String master,
                                   String username,
                                   String password,
                                   String tenantName,
                                   String domainName,
                                   String endpoint,
                                   List<String> regions,
                                   Boolean insecure,
                                   String heatTemplateLocation,
                                   LbaasConfig lbaasConfig) {
    this(accountName, environment, accountType, master, username, password, null, tenantName, domainName, endpoint, regions, insecure, heatTemplateLocation, lbaasConfig)
  }

  OpenstackNamedAccountCredentials(String accountName,
                                   String environment,
                                   String accountType,
                                   String master,
                                   String username,
                                   String password,
                                   List<String> requiredGroupMembership,
                                   String tenantName,
                                   String domainName,
                                   String endpoint,
                                   List<String> regions,
                                   Boolean insecure,
                                   String heatTemplateLocation,
                                   LbaasConfig lbaasConfig) {
    this.name = accountName
    this.environment = environment
    this.accountType = accountType
    this.master = master
    this.username = username
    this.password = password
    this.tenantName = tenantName
    this.domainName = domainName
    this.endpoint = endpoint
    this.requiredGroupMembership = requiredGroupMembership
    this.regions = regions
    this.insecure = insecure
    this.heatTemplateLocation = heatTemplateLocation
    this.lbaasConfig = lbaasConfig
    this.credentials = buildCredentials()
  }

  private OpenstackCredentials buildCredentials() {
    new OpenstackCredentials(this)
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  /**
   * Note: this is needed because there is an interface method of this name that should be called
   * in lieu of the synthetic getter for the credentials instance variable.
   * @return
   */
  @Override
  OpenstackCredentials getCredentials() {
    credentials
  }

}
