/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class EcsTask implements Instance, Serializable {
  private String name;
  private HealthState healthState;
  private Long launchTime;
  private String zone;
  private List<Map<String, String>> health;
  private String providerType;
  private String cloudProvider;

  public EcsTask(String name, Task task, InstanceStatus ec2Instance, List<Map<String, String>> health) {
    this.name = name;
    providerType = cloudProvider = EcsCloudProvider.ID;
    launchTime = task.getStartedAt() != null ? task.getStartedAt().getTime() : null;
    this.health =  health;
    healthState = calculateHealthState(task.getLastStatus(), task.getDesiredStatus());
    zone = ec2Instance.getAvailabilityZone();
  }

  /**
   * Maps the Last Status and Desired Status of a Tasks to a Health State understandable by Spinnaker
   *
   * The mapping is based on:
   *
   * Task Life Cycle: http://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_life_cycle.html
   *
   * @param lastStatus    Last reported status of the Task
   * @param desiredStatus Desired status of the Task
   * @return              Spinnaker understandable Health State
   */
  private HealthState calculateHealthState(String lastStatus, String desiredStatus) {
    HealthState currentState = null;

    if ("RUNNING".equals(desiredStatus) && "PENDING".equals(lastStatus)) {
      currentState = HealthState.Starting;
    } else if ("RUNNING".equals(lastStatus)) {
      currentState = HealthState.Up;
    } else if ("STOPPED".equals(desiredStatus)) {
      currentState = HealthState.Down;
    } else {
      currentState = HealthState.Unknown;
    }

    return currentState;
  }
}
