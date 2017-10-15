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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import org.springframework.stereotype.Component;

@Component
public class KubernetesReplicaSetDeployer extends KubernetesDeployer<V1beta1ReplicaSet> implements CanResize, CanDelete<V1DeleteOptions> {
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.REPLICA_SET;
  }

  @Override
  public KubernetesApiVersion apiVersion() {
    return KubernetesApiVersion.EXTENSIONS_V1BETA1;
  }

  @Override
  public Class<V1beta1ReplicaSet> getDeployedClass() {
    return V1beta1ReplicaSet.class;
  }

  @Override
  public Class<V1DeleteOptions> getDeleteOptionsClass() {
    return V1DeleteOptions.class;
  }

  @Override
  void deploy(KubernetesV2Credentials credentials, V1beta1ReplicaSet resource) {
    credentials.createReplicaSet(resource);
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
  public void resize(KubernetesV2Credentials credentials, String namespace, String name, Capacity capacity) {
    credentials.resizeReplicaSet(namespace, name, capacity.getDesired());
  }

  @Override
  public void delete(KubernetesV2Credentials credentials, String namespace, String name, V1DeleteOptions deleteOptions) {
    credentials.deleteReplicaSet(namespace, name, deleteOptions);
  }
}
