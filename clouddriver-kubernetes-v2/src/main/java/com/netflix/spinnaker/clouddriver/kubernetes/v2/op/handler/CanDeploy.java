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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.openapi.models.V1DeleteOptions;

public interface CanDeploy {
  default OperationResult deploy(
      KubernetesV2Credentials credentials,
      KubernetesManifest manifest,
      KubernetesManifestStrategy.DeployStrategy deployStrategy) {
    // If the manifest has a generateName, we must apply with kubectl create as all other operations
    // require looking up a manifest by name, which will fail.
    if (manifest.hasGenerateName()) {
      KubernetesManifest result = credentials.create(manifest);
      return new OperationResult().addManifest(result);
    }

    KubernetesManifest deployedManifest;
    switch (deployStrategy) {
      case RECREATE:
        try {
          credentials.delete(
              manifest.getKind(),
              manifest.getNamespace(),
              manifest.getName(),
              new KubernetesSelectorList(),
              new V1DeleteOptions());
        } catch (KubectlJobExecutor.KubectlException ignored) {
        }
        deployedManifest = credentials.deploy(manifest);
        break;
      case REPLACE:
        deployedManifest = credentials.createOrReplace(manifest);
        break;
      case APPLY:
        deployedManifest = credentials.deploy(manifest);
        break;
      default:
        throw new AssertionError(String.format("Unknown deploy strategy: %s", deployStrategy));
    }
    return new OperationResult().addManifest(deployedManifest);
  }
}
