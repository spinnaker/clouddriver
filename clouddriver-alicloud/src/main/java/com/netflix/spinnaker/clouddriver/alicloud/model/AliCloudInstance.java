/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class AliCloudInstance implements Instance {

  private String name;
  private Long launchTime;
  private String zone;
  final String cloudProvider = AliCloudProvider.ID;
  private HealthState healthState;
  private List<Map<String, Object>> health;

  private Map<String, Object> attributes = new HashMap<>();

  public AliCloudInstance(
      String name,
      Long launchTime,
      String zone,
      HealthState healthState,
      List<Map<String, Object>> health) {
    this.name = name;
    this.launchTime = launchTime;
    this.zone = zone;
    this.healthState = healthState;
    this.health = health;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Long getLaunchTime() {
    return launchTime;
  }

  @Override
  public String getZone() {
    return zone;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public HealthState getHealthState() {
    return healthState;
  }

  @Override
  public List<Map<String, Object>> getHealth() {
    return health;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, Object> attributes) {
    this.attributes = attributes;
  }
}
