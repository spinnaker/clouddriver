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

package com.netflix.spinnaker.clouddriver.consul.config

import com.netflix.spinnaker.clouddriver.consul.api.v1.ConsulCatalog

import java.util.concurrent.TimeUnit

class ConsulConfig {
  boolean enabled
  // required: reachable Consul server endpoints (IP address or DNS name)
  List<String> servers
  // optional: datacenters to cache/keep updated
  List<String> datacenters
  // optional: Port consul is running on for every agent
  Integer agentPort

  // Since this is config injected into every participating provider's Spring config, there is no easy way to
  // standardize where default values should come from. Instead, we require this method to be called after the
  // config is loaded.
  void applyDefaults() {
    if (!enabled) {
      throw new IllegalStateException("Consul not enabled, cannot set defaults")
    }

    if (!agentPort) {
      agentPort = 8500 // Default used by consul
    }

    if (!servers) {
      throw new IllegalArgumentException("Consul servers must be provided.")
    }

    if (!datacenters) {
      datacenters = (new ConsulCatalog(servers[0], ConsulProperties.DEFAULT_TIMEOUT_MILLIS)).api.datacenters()
    }
  }
}

class ConsulProperties {
  static long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(2)
}
