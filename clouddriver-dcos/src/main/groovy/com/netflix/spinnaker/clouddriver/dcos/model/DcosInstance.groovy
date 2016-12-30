/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import mesosphere.marathon.client.model.v2.Task
/**
 * Equivalent of a DCOS {@link mesosphere.marathon.client.model.v2.Task}
 */
class DcosInstance implements Instance {

  Task task
  final String providerType = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  DcosInstance(Task task) {
    this.task = task
  }

  @Override
  String getName() {
    task.id
  }

  @Override
  Long getLaunchTime() {
    Long.getLong(task.startedAt)
  }

  @Override
  String getZone() {
    null
  }

  @Override
  HealthState getHealthState() {
    List<Map<String, Object>> healthList = getHealth()
    someUpRemainingUnknown(healthList) ? HealthState.Up :
      anyStarting(healthList) ? HealthState.Starting :
        anyDown(healthList) ? HealthState.Down :
          anyOutOfService(healthList) ? HealthState.OutOfService : HealthState.Unknown
  }

  @Override
  List<Map<String, Object>> getHealth() {
    health
  }

//  boolean getIsHealthy() {
//    health ? health.any { it.state == 'Up' } && health.every { it.state == 'Up' || it.state == 'Unknown' } : false
//  }

  private static boolean anyDown(List<Map<String, Object>> healthList) {
    healthList.any { it.state == HealthState.Down.toString() }
  }

  private static boolean someUpRemainingUnknown(List<Map<String, Object>> healthList) {
    List<Map<String, Object>> knownHealthList = healthList.findAll { it.state != HealthState.Unknown.toString() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.toString() } : false
  }

  private static boolean anyStarting(List<Map<String, Object>> healthList) {
    healthList.any { it.state == HealthState.Starting.toString() }
  }

  private static boolean anyOutOfService(List<Map<String, Object>> healthList) {
    healthList.any { it.state == HealthState.OutOfService.toString() }
  }

  @Override
  boolean equals(Object o) {
    o instanceof DcosInstance ? o.task.id == task.id : false
  }

  @Override
  int hashCode() {
    return task.id.hashCode()
  }
}
