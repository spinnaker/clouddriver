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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesDaemonSetCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta2DaemonSet;
import io.kubernetes.client.models.V1beta2DaemonSetStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesDaemonSetHandler extends KubernetesHandler implements
    CanResize,
    CanDelete,
    CanPauseRollout,
    CanResumeRollout,
    CanUndoRollout,
    ServerGroupHandler {

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.DAEMON_SET;
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
    return KubernetesDaemonSetCachingAgent.class;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1beta2DaemonSet v1beta2DaemonSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2DaemonSet.class);
    return status(v1beta2DaemonSet);
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  private Status status(V1beta2DaemonSet daemonSet) {
    Status result = new Status();

    V1beta2DaemonSetStatus status = daemonSet.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    int desiredReplicas = status.getDesiredNumberScheduled();
    Integer existing = status.getCurrentNumberScheduled();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be scheduled");
    }

    existing = status.getUpdatedNumberScheduled();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all updated replicas to be scheduled");
    }

    existing = status.getNumberAvailable();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be available");
    }

    existing = status.getNumberReady();
    if (existing == null || desiredReplicas > existing) {
      return result.unstable("Waiting for all replicas to be ready");
    }

    return result;
  }
}
