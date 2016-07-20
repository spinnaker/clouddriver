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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.api.OSClient
import org.openstack4j.core.transport.Config
import org.openstack4j.model.identity.v2.Access
import org.openstack4j.openstack.OSFactory

class OpenstackIdentityV2Provider implements OpenstackIdentityProvider, OpenstackRequestHandler {

  OpenstackNamedAccountCredentials credentials
  Access access

  /**
   * Default constructor .. Requires regions are configured externally
   * as v2 openstack API doesn't support looking up regions.
   * @param client
   * @param regions - List of region ids
   */
  OpenstackIdentityV2Provider(OpenstackNamedAccountCredentials credentials) {
    this.credentials = credentials
    this.access = buildClient().access
  }

  @Override
  OSClient buildClient() {
    handleRequest {
      Config config = credentials.insecure ? Config.newConfig().withSSLVerificationDisabled() : Config.newConfig()
      OSFactory.builderV2()
        .withConfig(config)
        .endpoint(credentials.endpoint)
        .credentials(credentials.username, credentials.password)
        .tenantName(credentials.tenantName)
        .authenticate()
    }
  }

  @Override
  OSClient getClient() {
    if (tokenExpired) {
      access = buildClient().access
    }
    OSFactory.clientFromAccess(access)
  }

  @Override
  String getTokenId() {
    access.token.id
  }

  @Override
  boolean isTokenExpired() {
    long now = System.currentTimeMillis()
    long expires = access.token.expires.time
    now >= expires
  }

  //identity v2 specific operations

  /**
   * Regions are ultimately provided by configuration in v2.
   * @return
   */
  @Override
  List<String> getAllRegions() {
    credentials.regions
  }

  @Override
  OSClient getRegionClient(String region) {
    client.useRegion(region)
  }

}
