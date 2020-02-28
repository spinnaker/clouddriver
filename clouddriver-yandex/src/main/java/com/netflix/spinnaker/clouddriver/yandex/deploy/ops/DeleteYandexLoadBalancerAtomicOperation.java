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

import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.*;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DeleteYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexLoadBalancerProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import yandex.cloud.api.operation.OperationOuterClass;

public class DeleteYandexLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired private YandexOperationPoller operationPoller;
  @Autowired private YandexLoadBalancerProvider yandexLoadBalancerProvider;

  private DeleteYandexLoadBalancerDescription description;

  public DeleteYandexLoadBalancerAtomicOperation(DeleteYandexLoadBalancerDescription description) {
    this.description = description;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Void operate(List priorOutputs) {
    String name = description.getLoadBalancerName();
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing deletion of load balancer " + description.getLoadBalancerName() + "...");

    ListNetworkLoadBalancersRequest listRequest =
        ListNetworkLoadBalancersRequest.newBuilder()
            .setFolderId(description.getCredentials().getFolder())
            .setFilter("name='" + name + "'")
            .build();
    ListNetworkLoadBalancersResponse response =
        description.getCredentials().networkLoadBalancerService().list(listRequest);
    if (response.getNetworkLoadBalancersCount() != 1) {
      String message = "Found none of more than one load balancer with name '" + name + "'.";
      getTask().updateStatus(BASE_PHASE, message);
      throw new IllegalStateException(message);
    }
    DeleteNetworkLoadBalancerRequest deleteRequest =
        DeleteNetworkLoadBalancerRequest.newBuilder()
            .setNetworkLoadBalancerId(response.getNetworkLoadBalancers(0).getId())
            .build();
    OperationOuterClass.Operation deleteOperation =
        description.getCredentials().networkLoadBalancerService().delete(deleteRequest);
    operationPoller.waitDone(description.getCredentials(), deleteOperation, BASE_PHASE);
    getTask()
        .updateStatus(
            BASE_PHASE, "Done deleting load balancer " + description.getLoadBalancerName() + ".");
    return null;
  }
}
