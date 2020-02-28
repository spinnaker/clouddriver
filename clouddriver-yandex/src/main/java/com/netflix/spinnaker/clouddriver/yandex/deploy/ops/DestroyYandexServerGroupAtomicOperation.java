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

import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.DeleteInstanceGroupRequest;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.DetachNetworkLoadBalancerTargetGroupRequest;
import static yandex.cloud.api.operation.OperationOuterClass.Operation;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DestroyYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import groovy.util.logging.Slf4j;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("rawtypes")
@Slf4j
public class DestroyYandexServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP";
  private final DestroyYandexServerGroupDescription description;
  @Autowired private YandexOperationPoller operationPoller;
  @Autowired private YandexClusterProvider yandexClusterProvider;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public DestroyYandexServerGroupAtomicOperation(DestroyYandexServerGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String serverGroupName = description.getServerGroupName();
    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing destruction of server group " + serverGroupName + "... ");

    YandexCloudServerGroup serverGroup =
        yandexClusterProvider.getServerGroup(
            description.getAccount(), "ru-central1", serverGroupName);
    detachFromLoadBalancers(serverGroup.getLoadBalancerIntegration());
    destroyInstanceGroup(serverGroup.getId());
    getTask().updateStatus(BASE_PHASE, "Done destroying server group " + serverGroupName);
    return null;
  }

  public void detachFromLoadBalancers(
      YandexCloudServerGroup.LoadBalancerIntegration loadBalancerIntegration) {
    getTask().updateStatus(BASE_PHASE, "Checking for associated load balancers...");
    if (loadBalancerIntegration == null || loadBalancerIntegration.getBalancers() == null) {
      return;
    }

    getTask().updateStatus(BASE_PHASE, "Detaching server group from associated load balancers...");
    try {

      loadBalancerIntegration.getBalancers().stream()
          .map(
              b ->
                  DetachNetworkLoadBalancerTargetGroupRequest.newBuilder()
                      .setNetworkLoadBalancerId(b.getId())
                      .setTargetGroupId(loadBalancerIntegration.getTargetGroupId())
                      .build())
          .map(
              request ->
                  description
                      .getCredentials()
                      .networkLoadBalancerService()
                      .detachTargetGroup(request))
          .forEach(
              operation ->
                  operationPoller.waitDone(description.getCredentials(), operation, BASE_PHASE));
    } catch (StatusRuntimeException e) {
      if (e.getStatus() != Status.INVALID_ARGUMENT) {
        throw e;
      }
    }
    getTask().updateStatus(BASE_PHASE, "Detached server group from associated load balancers");
  }

  public void destroyInstanceGroup(String instanceGroupId) {
    DeleteInstanceGroupRequest request =
        DeleteInstanceGroupRequest.newBuilder().setInstanceGroupId(instanceGroupId).build();
    Operation deleteOperation = description.getCredentials().instanceGroupService().delete(request);
    operationPoller.waitDone(description.getCredentials(), deleteOperation, BASE_PHASE);
    getTask().updateStatus(BASE_PHASE, "Deleted instance group.");
  }
}
