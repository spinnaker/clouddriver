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

package com.netflix.spinnaker.clouddriver.consul.model

import com.netflix.spinnaker.clouddriver.consul.api.v1.model.CheckResult
import com.netflix.spinnaker.clouddriver.model.DiscoveryHealth
import com.netflix.spinnaker.clouddriver.model.HealthState

class ConsulHealth extends DiscoveryHealth {
  @Override
  public static String getDiscoveryType() {
    return "Consul"
  }

  CheckResult result

  String source

  ConsulHealth(CheckResult result) {
    this.result = result
    this.source = result.checkId
  }

  @Override
  HealthState getState() {
    switch (result.status) {
      case CheckResult.Status.passing:
        return HealthState.Up
      case CheckResult.Status.critical:
      case CheckResult.Status.warning:
        return HealthState.Down
      default:
        return HealthState.Unknown
    }
  }
}
