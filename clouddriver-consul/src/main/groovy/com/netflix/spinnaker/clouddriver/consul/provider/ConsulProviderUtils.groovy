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

package com.netflix.spinnaker.clouddriver.consul.provider

import com.netflix.spinnaker.clouddriver.consul.api.v1.ConsulAgent
import com.netflix.spinnaker.clouddriver.consul.api.v1.model.CheckResult
import com.netflix.spinnaker.clouddriver.consul.api.v1.model.ServiceResult
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.model.ConsulHealth
import com.netflix.spinnaker.clouddriver.consul.model.ConsulNode
import com.netflix.spinnaker.clouddriver.consul.model.ConsulService
import retrofit.RetrofitError

class ConsulProviderUtils {
  static ConsulNode getHealths(ConsulConfig config, String agent) {
    def healths = []
    def services = []
    def running = false
    try {
      healths = new ConsulAgent(config, agent).api.checks()?.collect { String name, CheckResult result ->
        return new ConsulHealth(result: result, source: result.checkID)
      } ?: []
      services = new ConsulAgent(config, agent).api.services()?.collect { String name, ServiceResult result ->
        return new ConsulService(result)
      } ?: []
      running = true
    } catch (RetrofitError e) {
    }
    return new ConsulNode(healths: healths, running: running, services: services)
  }

  // Returns true i.f.f. this "server group" of nodes is running consul.
  static boolean consulServerGroupDiscoverable(List<ConsulNode> nodes) {
    nodes?.any { node -> // If any nodes have consul running, we see if they have registered any services or checks.
      node?.running && (node?.services || node?.healths)
    } ?: false
  }

  // The return value of this function is non-sensical when `!consulServerGroup(nodes)` holds. (It will also possibly NPE).
  static boolean serverGroupDisabled(List<ConsulNode> nodes) {
    nodes.every { node -> // If every node isn't discoverable through consul, we say it's disabled.
      node.isDisabled()
    }
  }
}
