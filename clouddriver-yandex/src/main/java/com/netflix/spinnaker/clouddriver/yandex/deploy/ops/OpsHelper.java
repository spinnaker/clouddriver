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

import static yandex.cloud.api.loadbalancer.v1.HealthCheckOuterClass.HealthCheck;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass.AttachedTargetGroup;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass.NetworkLoadBalancer;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.AttachNetworkLoadBalancerTargetGroupRequest;
import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerServiceOuterClass.GetNetworkLoadBalancerRequest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupConverter;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import yandex.cloud.api.operation.OperationOuterClass;

public class OpsHelper {
  public static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public static void enableInstanceGroup(
      YandexOperationPoller operationPoller,
      String phaseName,
      YandexCloudCredentials credentials,
      String targetGroupId,
      Map<String, List<YandexCloudServerGroup.HealthCheckSpec>> loadBalancersSpecs) {
    if (targetGroupId == null) {
      return;
    }
    getTask().updateStatus(phaseName, "Registering instances with network load balancers...");
    getTask().updateStatus(phaseName, "Retrieving load balancers...");
    List<NetworkLoadBalancer> balancers =
        loadBalancersSpecs.keySet().stream()
            .map(lbId -> resolverLoadBalancer(credentials, phaseName, lbId))
            .collect(Collectors.toList());

    balancers.forEach(
        balancer -> {
          AttachedTargetGroup.Builder targetGroup =
              AttachedTargetGroup.newBuilder().setTargetGroupId(targetGroupId);

          List<YandexCloudServerGroup.HealthCheckSpec> healthCheckSpecs =
              loadBalancersSpecs.get(balancer.getId());
          for (int idx = 0; idx < healthCheckSpecs.size(); idx++) {
            HealthCheck healthCheck =
                mapHealthCheckSpec(targetGroupId, idx, healthCheckSpecs.get(idx));
            targetGroup.addHealthChecks(healthCheck);
          }

          AttachNetworkLoadBalancerTargetGroupRequest request =
              AttachNetworkLoadBalancerTargetGroupRequest.newBuilder()
                  .setNetworkLoadBalancerId(balancer.getId())
                  .setAttachedTargetGroup(targetGroup)
                  .build();

          getTask()
              .updateStatus(
                  phaseName,
                  "Registering server group with load balancer " + balancer.getName() + "...");
          OperationOuterClass.Operation operation =
              credentials.networkLoadBalancerService().attachTargetGroup(request);
          operationPoller.waitDone(credentials, operation, phaseName);
          getTask()
              .updateStatus(
                  phaseName,
                  "Done registering server group with load balancer " + balancer.getName() + ".");
        });
  }

  private static HealthCheck mapHealthCheckSpec(
      String targetGroupId, int index, YandexCloudServerGroup.HealthCheckSpec hc) {
    HealthCheck.Builder builder = HealthCheck.newBuilder();
    if (hc.getType() == YandexCloudServerGroup.HealthCheckSpec.Type.HTTP) {
      builder.setHttpOptions(
          HealthCheck.HttpOptions.newBuilder().setPort(hc.getPort()).setPath(hc.getPath()));
    } else {
      builder.setTcpOptions(HealthCheck.TcpOptions.newBuilder().setPort(hc.getPort()));
    }
    return builder
        .setName(targetGroupId + "-" + index)
        .setInterval(YandexInstanceGroupConverter.mapDuration(hc.getInterval()))
        .setTimeout(YandexInstanceGroupConverter.mapDuration(hc.getTimeout()))
        .setUnhealthyThreshold(hc.getUnhealthyThreshold())
        .setHealthyThreshold(hc.getHealthyThreshold())
        .build();
  }

  private static NetworkLoadBalancer resolverLoadBalancer(
      YandexCloudCredentials credentials, String phaseName, String id) {

    try {
      return credentials
          .networkLoadBalancerService()
          .get(GetNetworkLoadBalancerRequest.newBuilder().setNetworkLoadBalancerId(id).build());
    } catch (StatusRuntimeException e) {
      String message = "Could not resolve load balancer with id '" + id + "'.";
      getTask().updateStatus(phaseName, message);
      throw new IllegalStateException(message);
    }
  }
}
