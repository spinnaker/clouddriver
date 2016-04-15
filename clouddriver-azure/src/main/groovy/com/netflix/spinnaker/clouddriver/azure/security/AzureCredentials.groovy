/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.security

import com.netflix.spinnaker.clouddriver.azure.client.AzureBaseClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureNetworkClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureStorageClient

class AzureCredentials {

  final String tenantId
  final String clientId
  final String appKey
  final String project
  final String subscriptionId

  final AzureResourceManagerClient resourceManagerClient
  final AzureNetworkClient networkClient
  final AzureComputeClient computeClient
  final AzureStorageClient storageClient

  AzureCredentials(String tenantId, String clientId, String appKey, String subscriptionId) {
    this.tenantId = tenantId
    this.clientId = clientId
    this.appKey = appKey
    this.subscriptionId = subscriptionId
    this.project = "AzureProject"

    def token = AzureBaseClient.getTokenCredentials(this.clientId, this.tenantId, this.appKey)

    resourceManagerClient = new AzureResourceManagerClient(this.subscriptionId, token)

    networkClient = new AzureNetworkClient(this.subscriptionId, token)

    computeClient = new AzureComputeClient(this.subscriptionId, token)

    storageClient = new AzureStorageClient(this.subscriptionId, token)
    registerProviders()
  }

  /**
   * For each client, register the associated provider.
   */
  private void registerProviders() {
    resourceManagerClient.register(resourceManagerClient)
    networkClient.register(resourceManagerClient)
    computeClient.register(resourceManagerClient)
    storageClient.register(resourceManagerClient)
  }
}
