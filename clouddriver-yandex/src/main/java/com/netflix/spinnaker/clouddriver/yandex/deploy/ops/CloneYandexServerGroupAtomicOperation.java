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

import com.google.common.base.Strings;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexDeployHandler;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("rawtypes")
public class CloneYandexServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_SERVER_GROUP";

  private final YandexInstanceGroupDescription description;

  @Autowired private YandexDeployHandler deployHandler;
  @Autowired private YandexClusterProvider yandexClusterProvider;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public CloneYandexServerGroupAtomicOperation(YandexInstanceGroupDescription description) {
    this.description = description;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    YandexInstanceGroupDescription newDescription = cloneAndOverrideDescription();

    YandexServerGroupNameResolver serverGroupNameResolver =
        new YandexServerGroupNameResolver(newDescription.getCredentials());
    String clusterName =
        serverGroupNameResolver.combineAppStackDetail(
            newDescription.getApplication(),
            newDescription.getStack(),
            newDescription.getFreeFormDetails());

    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing copy of server group for cluster " + clusterName + "...");

    DeploymentResult result = deployHandler.handle(newDescription, priorOutputs);

    String newServerGroupName = result.getDeployments().iterator().next().getServerGroupName();
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Finished copying server group for cluster "
                + clusterName
                + ". "
                + "New server group = "
                + newServerGroupName
                + ".");

    return result;
  }

  private YandexInstanceGroupDescription cloneAndOverrideDescription() {
    String serverGroupName = description.getSource().getServerGroupName();
    if (Strings.isNullOrEmpty(serverGroupName)) {
      return description;
    }

    getTask()
        .updateStatus(BASE_PHASE, "Initializing copy of server group " + serverGroupName + "...");

    // Locate the ancestor server group.
    YandexCloudServerGroup ancestorServerGroup =
        yandexClusterProvider.getServerGroup(
            description.getAccount(), "ru-central1", serverGroupName);
    if (ancestorServerGroup == null) {
      return description;
    }

    YandexInstanceGroupDescription.YandexInstanceGroupDescriptionBuilder newDescription =
        description.toBuilder();

    // Override any ancestor values that were specified directly on the cloneServerGroup call.
    Names ancestorNames = Names.parseName(ancestorServerGroup.getName());
    newDescription.application(firstNonEmpty(description.getApplication(), ancestorNames.getApp()));
    newDescription.stack(firstNonEmpty(description.getStack(), ancestorNames.getStack()));
    newDescription.freeFormDetails(
        firstNonEmpty(description.getFreeFormDetails(), ancestorNames.getDetail()));

    newDescription.description(
        firstNonEmpty(description.getDescription(), ancestorNames.getDetail()));
    newDescription.zones(firstNonEmpty(description.getZones(), ancestorServerGroup.getZones()));
    newDescription.labels(firstNonEmpty(description.getLabels(), ancestorServerGroup.getLabels()));
    newDescription.targetSize(
        firstNotNull(
            description.getTargetSize(),
            ancestorServerGroup.getCapacity().getDesired().longValue()));
    newDescription.autoScalePolicy(
        firstNotNull(description.getAutoScalePolicy(), ancestorServerGroup.getAutoScalePolicy()));
    newDescription.deployPolicy(
        firstNotNull(description.getDeployPolicy(), ancestorServerGroup.getDeployPolicy()));
    YandexCloudServerGroup.TargetGroupSpec ancestorTargetGroupSpec =
        ancestorServerGroup.getLoadBalancerIntegration() == null
            ? null
            : ancestorServerGroup.getLoadBalancerIntegration().getTargetGroupSpec();
    newDescription.targetGroupSpec(
        firstNotNull(description.getTargetGroupSpec(), ancestorTargetGroupSpec));
    newDescription.healthCheckSpecs(
        firstNonEmpty(
            description.getHealthCheckSpecs(), ancestorServerGroup.getHealthCheckSpecs()));
    newDescription.serviceAccountId(
        firstNonEmpty(
            description.getServiceAccountId(), ancestorServerGroup.getServiceAccountId()));

    YandexCloudServerGroup.InstanceTemplate template = description.getInstanceTemplate();
    if (template != null) {
      YandexCloudServerGroup.InstanceTemplate ancestorTemplate =
          ancestorServerGroup.getInstanceTemplate();
      YandexCloudServerGroup.InstanceTemplate.InstanceTemplateBuilder builder =
          template.toBuilder();

      builder.description(
          firstNonEmpty(template.getDescription(), ancestorTemplate.getDescription()));
      builder.labels(firstNonEmpty(template.getLabels(), ancestorTemplate.getLabels()));
      builder.platformId(firstNonEmpty(template.getPlatformId(), ancestorTemplate.getPlatformId()));
      builder.resourcesSpec(
          firstNotNull(template.getResourcesSpec(), ancestorTemplate.getResourcesSpec()));
      builder.metadata(firstNonEmpty(template.getMetadata(), ancestorTemplate.getMetadata()));
      builder.bootDiskSpec(
          firstNotNull(template.getBootDiskSpec(), ancestorTemplate.getBootDiskSpec()));
      builder.secondaryDiskSpecs(
          firstNonEmpty(
              template.getSecondaryDiskSpecs(), ancestorTemplate.getSecondaryDiskSpecs()));
      builder.networkInterfaceSpecs(
          firstNonEmpty(
              template.getNetworkInterfaceSpecs(), ancestorTemplate.getNetworkInterfaceSpecs()));
      builder.schedulingPolicy(
          firstNotNull(template.getSchedulingPolicy(), ancestorTemplate.getSchedulingPolicy()));
      builder.serviceAccountId(
          firstNonEmpty(template.getServiceAccountId(), ancestorTemplate.getServiceAccountId()));

      newDescription.instanceTemplate(builder.build());
    } else {
      newDescription.instanceTemplate(ancestorServerGroup.getInstanceTemplate());
    }

    newDescription.enableTraffic(
        description.getEnableTraffic() != null && description.getEnableTraffic());
    newDescription.balancers(ancestorServerGroup.getLoadBalancersWithHealthChecks());

    return newDescription.build();
  }

  private static <T> T firstNotNull(T first, T second) {
    return first == null ? second : first;
  }

  private static String firstNonEmpty(String first, String second) {
    return Strings.isNullOrEmpty(first) ? second : first;
  }

  private static <T, COLLECTION extends Collection<T>> COLLECTION firstNonEmpty(
      COLLECTION first, COLLECTION second) {
    return first == null || first.isEmpty() ? second : first;
  }

  private static <K, V> Map<K, V> firstNonEmpty(Map<K, V> first, Map<K, V> second) {
    return first == null || first.isEmpty() ? second : first;
  }
}
