/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.servergroup;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.servergroup.KubernetesResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanResize;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class KubernetesResizeServerGroupOperation implements AtomicOperation<Void> {
  private final KubernetesResizeServerGroupDescription description;
  private final KubernetesCredentials credentials;
  private static final String OP_NAME = "RESIZE_KUBERNETES_SERVER_GROUP";

  public KubernetesResizeServerGroupOperation(KubernetesResizeServerGroupDescription description) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting resize operation...");
    KubernetesCoordinates coordinates = description.getCoordinates();

    getTask().updateStatus(OP_NAME, "Looking up resource properties...");
    KubernetesHandler deployer =
        credentials.getResourcePropertyRegistry().get(coordinates.getKind()).getHandler();

    if (!(deployer instanceof CanResize)) {
      throw new IllegalArgumentException(
          "Resource with " + coordinates + " does not support resize");
    }

    CanResize canResize = (CanResize) deployer;

    getTask().updateStatus(OP_NAME, "Calling resize operation...");
    canResize.resize(
        credentials, coordinates.getNamespace(), coordinates.getName(), description.getCapacity());

    return null;
  }
}
