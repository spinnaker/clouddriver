/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureServerGroupDescription extends AzureResourceOpsDescription {
  enum UpgradePolicy {
    Automatic, Manual
  }

  UpgradePolicy upgradePolicy
  AzureImage image
  AzureScaleSetSku sku
  AzureOperatingSystemConfig osConfig
  String provisioningState
  String application // TODO standardize between this and appName

  static class AzureImage {
    String publisher
    String offer
    String sku
    String version
  }

  static class AzureScaleSetSku {
    String name
    String tier
    Long capacity
  }

  static class AzureOperatingSystemConfig {
    String adminUserName
    String adminPassword
  }

  Integer getStorageAccountCount() {
    (sku.capacity / 20) + 1
  }

  String getIdenifier() {
    String.format("%s-%s-%s", application, stack, detail)
  }

  /*
   * TODO Still need to add in (or triage out) references to the following:
   *  ! LoadBalancers
   *  ! SecurityGroups
   *  - health threshold
   *  - avail zone
   *  + advanced
   *     - cooldown
   *     - health check type
   *     - health check grace period
   *     - termination policies
   *     - keypair name - Azure equivalent
   *     - ramdisk id
   *     - profile data
   *     - user data
   *     - monitoring
   *     - public IP Y/N/default
   *     - scaling process
   */
}
