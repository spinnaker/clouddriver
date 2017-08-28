package com.netflix.spinnaker.clouddriver.ecs.model;

import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class EcsTask implements Instance, Serializable {
  private String name;
  private HealthState healthState;
  private Long launchTime;
  private String zone;
  private List<Map<String, String>> health;
  private String providerType;
  private String cloudProvider;

  public EcsTask(String name, Task task, InstanceStatus ec2Instance) {
    this.name = name;
    providerType = cloudProvider = EcsCloudProvider.ID;
    launchTime = task.getStartedAt().getTime();
    health =  null;
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

  @Override
  public String getZone() {
    return zone;
  }

  @Override
  public List<Map<String, String>> getHealth() {
    return health;
  }

  @Override
  public String getProviderType() {
    return providerType;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public Long getLaunchTime() {
    return launchTime;
  }

  @Override
  public HealthState getHealthState() {
    return healthState;
  }

  @Override
  public String getName() {
    return name;
  }
}
