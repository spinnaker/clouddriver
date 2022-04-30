/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.List;

public interface HasPods {
  List<KubernetesManifest> pods(KubernetesCredentials credentials, KubernetesManifest object);

  static HasPods lookupProperties(ResourcePropertyRegistry registry, KubernetesKind kind) {
    KubernetesHandler hasPodsHandler = registry.get(kind).getHandler();
    if (!(hasPodsHandler instanceof HasPods)) {
      throw new IllegalArgumentException(
          "No support for pods via " + kind + " exists in Spinnaker");
    }

    return (HasPods) hasPodsHandler;
  }
}
