/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.config.Releases
import com.netflix.spinnaker.clouddriver.openstack.config.VersionType
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials

/**
 * Builds the appropriate {@link OpenstackClientProvider} based on the configuration.
 */
class OpenstackProviderFactory {

  static OpenstackClientProvider createProvider(OpenstackNamedAccountCredentials credentials) {
    OpenstackIdentityProvider identityProvider
    OpenstackComputeProvider computeProvider
    OpenstackNetworkingProvider networkingProvider
    OpenstackOrchestrationProvider orchestrationProvider
    OpenstackImageProvider imageProvider
    if (!(credentials.accountType in Releases.values().collect { it.value() })) {
      throw new IllegalArgumentException("Unknown openstack release ${credentials.accountType}")
    }
    identityProvider = createIdentityProvider(credentials)
    computeProvider = createComputeProvider(credentials, identityProvider)
    networkingProvider = createNetworkingProvider(credentials, identityProvider)
    orchestrationProvider = createOrchestrationProvider(credentials, identityProvider)
    imageProvider = createImageProvider(credentials, identityProvider)
    new OpenstackClientProvider(computeProvider, networkingProvider, orchestrationProvider, imageProvider)
  }

  /**
   * Set identity provider version.
   * @param credentials
   * @return
   */
  static OpenstackIdentityProvider createIdentityProvider(OpenstackNamedAccountCredentials credentials) {
    OpenstackIdentityProvider identityProvider = null
    if (credentials.identity) {
      switch (credentials.identity) {
        case VersionType.V2.value():
          identityProvider = new OpenstackIdentityV2Provider(credentials)
          break
        case VersionType.V3.value():
          identityProvider = new OpenstackIdentityV3Provider(credentials)
          break
        default:
          throw new IllegalArgumentException("Unknown identity provider ${credentials.identity}")
      }
    } else {
      if (Releases.KILO.value() == credentials.accountType) {
        identityProvider = new OpenstackIdentityV2Provider(credentials)
      } else if (Releases.LIBERTY.value() == credentials.accountType) {
        identityProvider = new OpenstackIdentityV3Provider(credentials)
      }
    }
    identityProvider
  }

  /**
   * Set compute provider version.
   * @param credentials
   * @param identityProvider
   * @return
     */
  static OpenstackComputeProvider createComputeProvider(OpenstackNamedAccountCredentials credentials, OpenstackIdentityProvider identityProvider) {
    OpenstackComputeProvider computeProvider
    if (credentials.compute) {
      switch (credentials.compute) {
        case VersionType.CURRENT.value():
          computeProvider = new OpenstackComputeProvider(identityProvider)
          break
        default:
          throw new IllegalArgumentException("Unknown compute provider ${credentials.compute}")
      }
    } else {
      computeProvider = new OpenstackComputeProvider(identityProvider)
    }
    computeProvider
  }

  /**
   * Set networking provider version.
   * @param credentials
   * @param identityProvider
   * @return
     */
  static OpenstackNetworkingProvider createNetworkingProvider(OpenstackNamedAccountCredentials credentials, OpenstackIdentityProvider identityProvider) {
    OpenstackNetworkingProvider networkingProvider
    if (credentials.networking) {
      switch (credentials.networking) {
        case VersionType.V1.value():
          networkingProvider = new OpenstackNetworkingV1Provider(identityProvider)
          break
        case VersionType.V2.value():
          throw new IllegalArgumentException("Networking provider ${credentials.networking} is not supported yet.")
          break
        default:
          throw new IllegalArgumentException("Unknown networking provider ${credentials.networking}")
      }
    } else {
      //TODO this should be changed to check the release name once V2 is available for liberty and beyond
      networkingProvider = new OpenstackNetworkingV1Provider(identityProvider)
    }
    networkingProvider
  }

  /**
   * Set orchestration provider version.
   * @param credentials
   * @param identityProvider
   * @return
     */
  static OpenstackOrchestrationProvider createOrchestrationProvider(OpenstackNamedAccountCredentials credentials, OpenstackIdentityProvider identityProvider) {
    OpenstackOrchestrationProvider orchestrationProvider
    if (credentials.orchestration) {
      switch (credentials.orchestration) {
        case VersionType.V1.value():
          orchestrationProvider = new OpenstackOrchestrationV1Provider(identityProvider)
          break
        default:
          throw new IllegalArgumentException("Unknown orchestration provider ${credentials.orchestration}")
      }
    } else {
      orchestrationProvider = new OpenstackOrchestrationV1Provider(identityProvider)
    }
    orchestrationProvider
  }

  /**
   * Set image provider version.
   * @param credentials
   * @param identityProvider
   * @return
   */
  static OpenstackImageProvider createImageProvider(OpenstackNamedAccountCredentials credentials, OpenstackIdentityProvider identityProvider) {
    OpenstackImageProvider imageProvider
    if (credentials.images) {
      switch (credentials.images) {
        case VersionType.V1.value():
          imageProvider = new OpenstackImageV1Provider(identityProvider)
          break
        case VersionType.V2.value():
          throw new IllegalArgumentException("Images provider ${credentials.images} is not supported yet.")
          break
        default:
          throw new IllegalArgumentException("Unknown networking provider ${credentials.images}")
      }
    } else {
      //TODO this should be changed to check the release name once V2 is available for liberty and beyond
      imageProvider = new OpenstackImageV1Provider(identityProvider)
    }
    imageProvider
  }

}
