/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.HORIZONTAL_POD_AUTOSCALER;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_ATTACHMENT_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.ResourceKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1CrossVersionObjectReference;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscaler;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscalerSpec;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscalerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KubernetesHorizontalPodAutoscalerHandler extends KubernetesHandler {
  @Nonnull
  @Override
  protected ImmutableList<Replacer> artifactReplacers() {
    return ImmutableList.of(Replacer.hpaDeployment(), Replacer.hpaReplicaSet());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_ATTACHMENT_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return HORIZONTAL_POD_AUTOSCALER;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.AUTOSCALERS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1HorizontalPodAutoscaler hpa =
        KubernetesCacheDataConverter.getResource(manifest, V1HorizontalPodAutoscaler.class);
    return status(hpa);
  }

  private Status status(V1HorizontalPodAutoscaler hpa) {
    V1HorizontalPodAutoscalerStatus status = hpa.getStatus();
    if (status == null) {
      return Status.noneReported();
    }

    int desiredReplicas = defaultToZero(status.getDesiredReplicas());
    int existing = defaultToZero(status.getCurrentReplicas());
    if (desiredReplicas > existing) {
      return Status.defaultStatus()
          .unstable(
              String.format(
                  "Waiting for HPA to complete a scale up, current: %d desired: %d",
                  existing, desiredReplicas));
    }

    if (desiredReplicas < existing) {
      return Status.defaultStatus()
          .unstable(
              String.format(
                  "Waiting for HPA to complete a scale down, current: %d desired: %d",
                  existing, desiredReplicas));
    }
    // desiredReplicas == existing, this is now stable
    return Status.defaultStatus();
  }

  // Unboxes an Integer, returning 0 if the input is null
  private static int defaultToZero(@Nullable Integer input) {
    return input == null ? 0 : input;
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  /**
   * Adds relationships between a horizontal pod autoscaler manifest and its targeted workloads to
   * the relationshipMap.
   */
  @Override
  public void addRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    Map<ResourceKey, KubernetesManifest> mapResourceKeyToManifest = new HashMap<>();
    Map<ResourceKey, List<KubernetesManifest>> deploymentResourceKeysToReplicaSets =
        new HashMap<>();

    allResources
        .getOrDefault(KubernetesKind.REPLICA_SET, new ArrayList<>())
        .forEach(
            r -> {
              addResourceKeysForKubernetesResource(mapResourceKeyToManifest, r);
              associateReplicaSetWithDeployments(deploymentResourceKeysToReplicaSets, r);
            });

    allResources
        .getOrDefault(KubernetesKind.STATEFUL_SET, new ArrayList<>())
        .forEach(
            s -> {
              addResourceKeysForKubernetesResource(mapResourceKeyToManifest, s);
            });

    for (KubernetesManifest hpa :
        allResources.getOrDefault(HORIZONTAL_POD_AUTOSCALER, new ArrayList<>())) {
      relationshipMap.put(
          hpa,
          getRelatedManifests(hpa, mapResourceKeyToManifest, deploymentResourceKeysToReplicaSets));
    }
  }

  private void addResourceKeysForKubernetesResource(
      Map<ResourceKey, KubernetesManifest> entries, KubernetesManifest resource) {
    ResourceKey resourceKey =
        new ResourceKey(resource.getKind(), resource.getName(), resource.getNamespace());
    entries.put(resourceKey, resource);
  }

  private void associateReplicaSetWithDeployments(
      Map<ResourceKey, List<KubernetesManifest>> deploymentsToReplicaSets,
      KubernetesManifest replicaSet) {
    List<ResourceKey> deploymentResourceKeys =
        replicaSet.getOwnerReferences().stream()
            .filter(o -> o.getKind().equals(KubernetesKind.DEPLOYMENT))
            .map(d -> new ResourceKey(d.getKind(), d.getName(), replicaSet.getNamespace()))
            .collect(Collectors.toList());

    for (ResourceKey deploymentResourceKey : deploymentResourceKeys) {
      deploymentsToReplicaSets
          .computeIfAbsent(deploymentResourceKey, d -> new ArrayList<>())
          .add(replicaSet);
    }
  }

  private List<KubernetesManifest> getRelatedManifests(
      KubernetesManifest hpaManifest,
      Map<ResourceKey, KubernetesManifest> mapLabelToManifest,
      Map<ResourceKey, List<KubernetesManifest>> deploymentsToReplicaSets) {

    V1HorizontalPodAutoscaler hpa =
        KubernetesCacheDataConverter.getResource(hpaManifest, V1HorizontalPodAutoscaler.class);

    String targetRefKind =
        Optional.ofNullable(hpa.getSpec())
            .map(V1HorizontalPodAutoscalerSpec::getScaleTargetRef)
            .map(V1CrossVersionObjectReference::getKind)
            .orElse("");

    String targetRefName =
        Optional.ofNullable(hpa.getSpec())
            .map(V1HorizontalPodAutoscalerSpec::getScaleTargetRef)
            .map(V1CrossVersionObjectReference::getName)
            .orElse("");

    String hpaNamespace =
        Optional.ofNullable(hpa.getMetadata()).map(V1ObjectMeta::getNamespace).orElse("");

    List<KubernetesManifest> result = new ArrayList<>();

    if (targetRefKind.isEmpty() || targetRefName.isEmpty() || hpaNamespace.isEmpty()) {
      return result;
    }

    ResourceKey scaleTargetRefResourceKey =
        new ResourceKey(KubernetesKind.fromString(targetRefKind), targetRefName, hpaNamespace);

    KubernetesManifest matchingReplicaSet = mapLabelToManifest.get(scaleTargetRefResourceKey);

    if (matchingReplicaSet != null) {
      result.add(matchingReplicaSet);
    } else {
      result = deploymentsToReplicaSets.getOrDefault(scaleTargetRefResourceKey, new ArrayList<>());
    }

    return result;
  }
}
