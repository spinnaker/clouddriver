/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.google.api.services.appengine.v1.model.Instance as AppEngineApiInstance
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance

class AppEngineInstance implements Instance, Serializable {
  String name
  Long launchTime
  AppEngineInstanceStatus instanceStatus
  String zone
  String serverGroup
  List<String> loadBalancers
  String providerType = AppEngineCloudProvider.ID

  AppEngineInstance() {}

  AppEngineInstance(AppEngineApiInstance instance) {
    this.instanceStatus = AppEngineInstanceStatus.valueOf(instance.getAvailability())
    this.name = instance.getId()
    this.launchTime = AppEngineModelUtil.translateTime(instance.getStartTime())
  }

  HealthState getHealthState() {
    HealthState.Up
  }

  List<Map<String, String>> getHealth() {
    null
  }

  enum AppEngineInstanceStatus {
    /*
    * See https://cloud.google.com/appengine/docs/java/how-instances-are-managed
    * */
    DYNAMIC,
    RESIDENT,
    UNKNOWN
  }
}
