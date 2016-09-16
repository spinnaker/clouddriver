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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.google.deploy.description.GoogleInstanceListDescription

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "disableInstancesInDiscovery": { "instanceIds": ["myapp-dev-v000-asdfax"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
 */
class DisableInstancesInDiscoveryOperation extends AbstractEnableDisableInstancesInDiscoveryOperation {
  DisableInstancesInDiscoveryOperation(GoogleInstanceListDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    return false
  }

  @Override
  String getPhaseName() {
    return "DISABLE_INSTANCES_IN_DISCOVERY"
  }
}
