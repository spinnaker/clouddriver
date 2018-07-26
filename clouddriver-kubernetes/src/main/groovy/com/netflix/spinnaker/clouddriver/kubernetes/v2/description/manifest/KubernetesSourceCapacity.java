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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.lang.Double;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubernetesSourceCapacity {

  public static Double getSourceCapacity(KubernetesManifest manifest, KubernetesV2Credentials credentials) {
    KubernetesManifest manifest = credentials.get(manifest.getKind(), manifest.getNamespace(), manifest.getName());
    if (manifest != null) {
      return manifest.getReplicas();
    }
    return null;
  }
}
