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

package com.netflix.spinnaker.clouddriver.google.model.health

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.Canonical

@Canonical
class GoogleInstanceHealth {

  Status status

  enum Status {
    PROVISIONING,
    STAGING,
    RUNNING,
    STOPPING,
    STOPPED,
    TERMINATED,
    SUSPENDING,
    SUSPENDED

    HealthState toHealthState() {
      switch (this) {
        case PROVISIONING:
        case STAGING:
          return HealthState.Starting
        case RUNNING:
          return HealthState.Up
        default:
          return HealthState.Down
      }
    }
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  class View extends GoogleHealth implements Health {

    final Type type = Type.Google
    final HealthClass healthClass = HealthClass.platform

    HealthState getState() {
      GoogleInstanceHealth.this.status?.toHealthState()
    }
  }
}
