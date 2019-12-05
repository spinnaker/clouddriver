/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sql.agent")
class SqlAgentProperties {
  var enabledPattern: String = ".*"

  /**
   * Optionally disable specific agents based on their fully qualified name
   */
  var disabledAgents: List<String> = emptyList()

  var maxConcurrentAgents: Int = 100
  var agentLockAcquisitionIntervalSeconds: Long = 1
  var poll: SqlPollProperties = SqlPollProperties()
}

class SqlPollProperties {
  var intervalSeconds: Long = 30
  var errorIntervalSeconds: Long = 30
  var timeoutSeconds: Long = 300
}
