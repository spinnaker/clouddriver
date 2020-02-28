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

import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.DetachNetworkLoadBalancerTargetGroupRequest;
import static yandex.cloud.api.operation.OperationOuterClass.Operation;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.EnableDisableYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class EnableDisableYandexServerGroupAtomicOperation implements AtomicOperation<Void> {
  public Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired private YandexClusterProvider yandexClusterProvider;
  @Autowired private YandexOperationPoller operationPoller;

  private EnableDisableYandexServerGroupDescription description;
  private boolean disable;

  public EnableDisableYandexServerGroupAtomicOperation(
      EnableDisableYandexServerGroupDescription description, boolean disable) {
    this.description = description;
    this.disable = disable;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Void operate(List priorOutputs) {
    String phaseName = disable ? "DISABLE_SERVER_GROUP" : "ENABLE_SERVER_GROUP";
    String verb = disable ? "disable" : "enable";
    String presentParticipling = disable ? "Disabling" : "Enabling";
    String serverGroupName = description.getServerGroupName();

    getTask()
        .updateStatus(
            phaseName,
            "Initializing " + verb + " server group operation for " + serverGroupName + "...");

    YandexCloudServerGroup serverGroup =
        yandexClusterProvider.getServerGroup(
            description.getAccount(), "ru-central1", serverGroupName);

    getTask()
        .updateStatus(phaseName, presentParticipling + " server group " + serverGroupName + "...");
    if (disable) {
      disableInstanceGroup(phaseName, serverGroup);
    } else {
      enableInstanceGroup(phaseName, serverGroup);
    }
    getTask()
        .updateStatus(
            phaseName,
            "Done " + presentParticipling.toLowerCase() + " server group " + serverGroupName + ".");
    return null;
  }

  private void enableInstanceGroup(String phaseName, YandexCloudServerGroup serverGroup) {
    OpsHelper.enableInstanceGroup(
        operationPoller,
        phaseName,
        description.getCredentials(),
        serverGroup.getLoadBalancerIntegration() == null
            ? null
            : serverGroup.getLoadBalancerIntegration().getTargetGroupId(),
        serverGroup.getLoadBalancersWithHealthChecks());
  }

  private void disableInstanceGroup(String phaseName, YandexCloudServerGroup serverGroup) {
    if (serverGroup.getLoadBalancerIntegration() == null) {
      return;
    }
    String targetGroupId = serverGroup.getLoadBalancerIntegration().getTargetGroupId();
    serverGroup
        .getLoadBalancerIntegration()
        .getBalancers()
        .forEach(
            balancer -> {
              DetachNetworkLoadBalancerTargetGroupRequest request =
                  DetachNetworkLoadBalancerTargetGroupRequest.newBuilder()
                      .setNetworkLoadBalancerId(balancer.getId())
                      .setTargetGroupId(targetGroupId)
                      .build();
              getTask()
                  .updateStatus(
                      phaseName,
                      "Deregistering server group from load balancer "
                          + balancer.getName()
                          + "...");

              try {
                Operation operation =
                    description
                        .getCredentials()
                        .networkLoadBalancerService()
                        .detachTargetGroup(request);
                operationPoller.waitDone(description.getCredentials(), operation, phaseName);
              } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() != Status.Code.INVALID_ARGUMENT) {
                  throw e;
                }
              }
              getTask()
                  .updateStatus(
                      phaseName,
                      "Done deregistering server group from load balancer "
                          + balancer.getName()
                          + ".");
            });
  }
}
