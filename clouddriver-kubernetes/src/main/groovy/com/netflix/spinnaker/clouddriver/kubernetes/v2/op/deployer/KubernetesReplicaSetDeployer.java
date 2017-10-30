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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.models.V1beta1ReplicaSetStatus;
import io.kubernetes.client.models.V1beta2ReplicaSet;
import io.kubernetes.client.models.V1beta2ReplicaSetStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesReplicaSetDeployer extends KubernetesDeployer implements CanResize, CanDelete<V1DeleteOptions> {
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.REPLICA_SET;
  }

  @Override
  public Class<V1DeleteOptions> getDeleteOptionsClass() {
    return V1DeleteOptions.class;
  }

  @Override
  public boolean versioned() {
    return true;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUP;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
      case EXTENSIONS_V1BETA1:
        V1beta1ReplicaSet v1beta1ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta1ReplicaSet.class);
        return status(v1beta1ReplicaSet);
      case APPS_V1BETA2:
        V1beta2ReplicaSet v1beta2ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2ReplicaSet.class);
        return status(v1beta2ReplicaSet);
      default:
        throw new UnsupportedVersionException(manifest);
    }
  }

  private Status status(V1beta1ReplicaSet replicaSet) {
    int desiredReplicas = replicaSet.getSpec().getReplicas();
    V1beta1ReplicaSetStatus status = replicaSet.getStatus();
    if (status == null) {
      return Status.unstable("No status reported yet");
    }

    Integer existing = status.getFullyLabeledReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be fully-labeled");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be available");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be ready");
    }

    return Status.stable();
  }

  private Status status(V1beta2ReplicaSet replicaSet) {
    int desiredReplicas = replicaSet.getSpec().getReplicas();
    V1beta2ReplicaSetStatus status = replicaSet.getStatus();
    if (status == null) {
      return Status.unstable("No status reported yet");
    }

    Integer existing = status.getFullyLabeledReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be fully-labeled");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be available");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be ready");
    }

    return Status.stable();
  }

  @Override
  public void resize(KubernetesV2Credentials credentials, String namespace, String name, Capacity capacity) {
    credentials.resizeReplicaSet(namespace, name, capacity.getDesired());
  }

  @Override
  public void delete(KubernetesV2Credentials credentials, String namespace, String name, V1DeleteOptions deleteOptions) {
    credentials.deleteReplicaSet(namespace, name, deleteOptions);
  }

  public static Map<String, String> getPodTemplateLabels(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
      case EXTENSIONS_V1BETA1:
        V1beta1ReplicaSet v1beta1ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta1ReplicaSet.class);
        return getPodTemplateLabels(v1beta1ReplicaSet);
      case APPS_V1BETA2:
        V1beta2ReplicaSet v1beta2ReplicaSet = KubernetesCacheDataConverter.getResource(manifest, V1beta2ReplicaSet.class);
        return getPodTemplateLabels(v1beta2ReplicaSet);
      default:
        throw new UnsupportedVersionException(manifest);
    }
  }

  private static Map<String, String> getPodTemplateLabels(V1beta1ReplicaSet replicaSet) {
    return replicaSet.getSpec().getTemplate().getMetadata().getLabels();
  }

  private static Map<String, String> getPodTemplateLabels(V1beta2ReplicaSet replicaSet) {
    return replicaSet.getSpec().getTemplate().getMetadata().getLabels();
  }
}
