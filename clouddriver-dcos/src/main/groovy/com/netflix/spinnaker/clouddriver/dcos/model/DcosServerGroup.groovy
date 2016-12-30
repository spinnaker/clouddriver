/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import mesosphere.marathon.client.model.v2.App

/**
 * Equivalent of a Dcos {@link mesosphere.marathon.client.model.v2.App}
 */
class DcosServerGroup implements ServerGroup, Serializable {

  App app
  final String type = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  DcosServerGroup(App app) {
    this.app = app
  }

  @Override
  String getName() {
    app.id
  }

  @Override
  String getRegion() {
    null
  }

  @Override
  Boolean isDisabled() {
    app.instances < 0
  }

  @Override
  Long getCreatedTime() {
    Long.getLong(app.versionInfo.lastConfigChangeAt)
  }

  @Override
  Set<String> getZones() {
    [] as Set
  }

  @Override
  Set<Instance> getInstances() {
    //TODO
    [] as Set
  }

  @Override
  Set<String> getLoadBalancers() {
    [] as Set
  }

  @Override
  Set<String> getSecurityGroups() {
    [] as Set
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    [:]
  }

  @Override
  Map<String, Object> getTags() {
    app.labels
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<DcosInstance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    return null
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    null
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }
}
