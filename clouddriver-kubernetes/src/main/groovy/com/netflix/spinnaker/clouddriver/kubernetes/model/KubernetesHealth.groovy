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

package com.netflix.spinnaker.clouddriver.kubernetes.model

import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Pod

class KubernetesHealth implements Health {
  HealthState state
  final String source
  final String type
  final String healthClass = "platform"

  KubernetesHealth(Pod pod) {
    source = "Pod"
    type = "KubernetesPod"
    def phase = pod.status.phase
    state = phase == "Pending" ? HealthState.OutOfService :
      phase == "Running" ? HealthState.Up :
        phase == "Succeeded" ? HealthState.Succeeded :
          phase == "Failed" ? HealthState.Failed : HealthState.Unknown
  }

  KubernetesHealth(String service, String enabled) {
    source = "Service $service"
    type = "KubernetesService"
    state = enabled == "true" ? HealthState.Up :
      enabled == "false" ? HealthState.OutOfService : HealthState.Unknown
  }

  KubernetesHealth(String name, ContainerStatus containerStatus) {
    source = "Container $name"
    type = "KubernetesContainer"

    state = HealthState.Unknown
    if (containerStatus.state.running) {
      if (containerStatus.ready) {
        state = HealthState.Up
      } else {
        state = HealthState.Down
      }
    } else if (containerStatus.state.terminated) {
      if (containerStatus.state.terminated.reason == "Completed") {
        if (containerStatus.state.terminated.exitCode == 0) {
          state = HealthState.Succeeded
        } else {
          state = HealthState.Failed
        }
      } else {
        state = HealthState.Down
      }
    } else if (containerStatus.state.waiting) {
      if (!containerStatus.ready) {
        state = HealthState.Starting
      }
    }
  }
}
