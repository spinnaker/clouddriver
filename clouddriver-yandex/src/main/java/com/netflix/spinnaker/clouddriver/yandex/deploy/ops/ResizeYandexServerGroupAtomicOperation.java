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

import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.ScalePolicy;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.UpdateInstanceGroupRequest;
import static yandex.cloud.api.operation.OperationOuterClass.Operation;

import com.google.protobuf.FieldMask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.ResizeYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ResizeYandexServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP";
  private final ResizeYandexServerGroupDescription description;

  @Autowired private YandexClusterProvider yandexClusterProvider;
  @Autowired private YandexOperationPoller operationPoller;

  public ResizeYandexServerGroupAtomicOperation(ResizeYandexServerGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(
        BASE_PHASE,
        "Initializing resize of server group " + description.getServerGroupName() + "...");

    String serverGroupName = description.getServerGroupName();
    YandexCloudServerGroup serverGroup =
        yandexClusterProvider.getServerGroup(
            description.getAccount(), "ru-central1", serverGroupName);
    if (serverGroup == null) {
      String message = "Not found server group '" + serverGroupName + "'.";
      task.updateStatus(BASE_PHASE, message);
      throw new IllegalStateException(message);
    }

    UpdateInstanceGroupRequest request =
        UpdateInstanceGroupRequest.newBuilder()
            .setInstanceGroupId(serverGroup.getId())
            .setUpdateMask(FieldMask.newBuilder().addPaths("scale_policy"))
            .setScalePolicy(
                ScalePolicy.newBuilder()
                    .setFixedScale(
                        ScalePolicy.FixedScale.newBuilder()
                            .setSize(description.getCapacity().getDesired())))
            .build();
    Operation operation = description.getCredentials().instanceGroupService().update(request);
    operationPoller.waitDone(description.getCredentials(), operation, BASE_PHASE);
    task.updateStatus(BASE_PHASE, "Done resizing server group " + serverGroupName);
    return null;
  }
}
