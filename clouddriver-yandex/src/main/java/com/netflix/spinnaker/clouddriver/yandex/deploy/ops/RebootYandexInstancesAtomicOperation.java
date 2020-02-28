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

import static yandex.cloud.api.compute.v1.InstanceServiceOuterClass.RestartInstanceRequest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.RebootYandexInstancesDescription;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import yandex.cloud.api.operation.OperationOuterClass;

public class RebootYandexInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "REBOOT_INSTANCES";

  @Autowired private YandexOperationPoller operationPoller;

  private final RebootYandexInstancesDescription description;

  public RebootYandexInstancesAtomicOperation(RebootYandexInstancesDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(
        BASE_PHASE,
        "Initializing reboot of instances ("
            + String.join(", ", description.getInstanceIds())
            + ")...");

    for (String instanceId : description.getInstanceIds()) {
      task.updateStatus(BASE_PHASE, "Attempting to reset instance " + instanceId + "...");
      RestartInstanceRequest request =
          RestartInstanceRequest.newBuilder().setInstanceId(instanceId).build();
      OperationOuterClass.Operation operation =
          description.getCredentials().instanceService().restart(request);
      operationPoller.waitDone(description.getCredentials(), operation, BASE_PHASE);
    }
    task.updateStatus(
        BASE_PHASE,
        "Done rebooting instances (" + String.join(", ", description.getInstanceIds()) + ").");
    return null;
  }
}
