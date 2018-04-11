/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesStatefulSetCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta2StatefulSet;
import io.kubernetes.client.models.V1beta2StatefulSetStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

@Component
public class KubernetesStatefulSetHandler extends KubernetesHandler implements
    CanResize,
    CanDelete,
    CanScale,
    CanPauseRollout,
    CanResumeRollout,
    CanUndoRollout,
    ServerGroupHandler {

  public KubernetesStatefulSetHandler() {
    registerReplacer(ArtifactReplacerFactory.dockerImageReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.secretVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapEnvFromReplacer());
    registerReplacer(ArtifactReplacerFactory.secretEnvFromReplacer());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_CONTROLLER_PRIORITY.getValue();
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.STATEFUL_SET;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesStatefulSetCachingAgent.class;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1beta2StatefulSet v1beta2StatefulSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2StatefulSet.class);
    return status(v1beta2StatefulSet);
  }

  public static String serviceName(KubernetesManifest manifest) {
    // TODO(lwander) perhaps switch on API version if this changes
    Map<String, Object> spec = (Map<String, Object>) manifest.get("spec");
    return (String) spec.get("serviceName");
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  private Status status(V1beta2StatefulSet statefulSet) {
    Status result = new Status();

    V1beta2StatefulSetStatus status = statefulSet.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    int desiredReplicas = statefulSet.getSpec().getReplicas();
    Integer existing = status.getReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for at least the desired replica count to be met");
    }

    if (!status.getCurrentRevision().equals(status.getUpdateRevision())) {
      return result.unstable("Waiting for the updated revision to match the current revision");
    }

    existing = status.getCurrentReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all updated replicas to be scheduled");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all updated replicas to be ready");
    }

    return result;
  }
}
