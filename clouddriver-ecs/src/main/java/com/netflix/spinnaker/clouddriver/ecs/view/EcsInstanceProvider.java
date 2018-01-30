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

package com.netflix.spinnaker.clouddriver.ecs.view;

import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EcsInstanceProvider implements InstanceProvider<EcsTask> {

  private final TaskCacheClient taskCacheClient;
  private final ContainerInstanceCacheClient containerInstanceCacheClient;
  private ContainerInformationService containerInformationService;

  @Autowired
  public EcsInstanceProvider(ContainerInformationService containerInformationService,
                             TaskCacheClient taskCacheClient,
                             ContainerInstanceCacheClient containerInstanceCacheClient) {
    this.containerInformationService = containerInformationService;
    this.taskCacheClient = taskCacheClient;
    this.containerInstanceCacheClient = containerInstanceCacheClient;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public EcsTask getInstance(String account, String region, String id) {
    if (!isValidId(id, region))
      return null;

    EcsTask ecsInstance = null;

    String key = Keys.getTaskKey(account, region, id);
    Task task = taskCacheClient.get(key);
    if (task == null) {
      return null;
    }

    key = Keys.getContainerInstanceKey(account, region, task.getContainerInstanceArn());
    ContainerInstance containerInstance = containerInstanceCacheClient.get(key);

    if (containerInstance != null) {
      String serviceName = StringUtils.substringAfter(task.getGroup(), "service:");
      Long launchTime = task.getStartedAt();

      List<Map<String, String>> healthStatus = containerInformationService.getHealthStatus(id, serviceName, account, region);
      String address = containerInformationService.getTaskPrivateAddress(account, region, task);

      ecsInstance = new EcsTask(id, launchTime, task.getLastStatus(), task.getDesiredStatus(), containerInstance.getAvailabilityZone(), healthStatus, address);
    }

    return ecsInstance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  private boolean isValidId(String id, String region) {
    String idRegex = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
    String idOnly = String.format("^%s$", idRegex);
    String arn = String.format("arn:aws:ecs:%s:\\d*:task/%s", region, idRegex);
    return id.matches(idOnly) || id.matches(arn);
  }

}

