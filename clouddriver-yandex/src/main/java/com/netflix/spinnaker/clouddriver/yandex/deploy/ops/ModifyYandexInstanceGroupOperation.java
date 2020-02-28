/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.InstanceGroup;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.*;
import static yandex.cloud.api.operation.OperationOuterClass.Operation;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupConverter;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ModifyYandexInstanceGroupOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_INSTANCE_GROUP";
  private final YandexInstanceGroupDescription description;

  @Autowired private YandexOperationPoller operationPoller;

  public ModifyYandexInstanceGroupOperation(YandexInstanceGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Initializing operation...");
    YandexCloudCredentials credentials = description.getCredentials();

    description.saturateLabels();
    task.updateStatus(
        BASE_PHASE, "Resolving server group identifier  " + description.getName() + "...");

    ListInstanceGroupsRequest listRequest =
        ListInstanceGroupsRequest.newBuilder()
            .setFolderId(credentials.getFolder())
            .setFilter("name='" + description.getName() + "'")
            .setView(InstanceGroupView.FULL)
            .build();

    List<InstanceGroup> instanceGroups =
        credentials.instanceGroupService().list(listRequest).getInstanceGroupsList();
    if (instanceGroups.size() != 1) {
      String message =
          "Found nothing or more than one server group '" + description.getName() + "'.";
      task.updateStatus(BASE_PHASE, message);
      throw new IllegalStateException(message);
    }

    String instanceGroupId = instanceGroups.get(0).getId();
    task.updateStatus(BASE_PHASE, "Composing server group " + description.getName() + "...");
    UpdateInstanceGroupRequest request =
        YandexInstanceGroupConverter.mapToUpdateRequest(description, instanceGroupId);

    Operation operation = credentials.instanceGroupService().update(request);
    operationPoller.waitDone(description.getCredentials(), operation, BASE_PHASE);
    task.updateStatus(BASE_PHASE, "Done updating server group " + description.getName() + ".");
    return null;
  }
}
