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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.view.model;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;

abstract public class ManifestBasedModel {
  public String getName() {
    return getManifest().getFullResourceName();
  }

  // Spinnaker namespace hacks
  public String getZone() {
    return getManifest().getNamespace();
  }

  // Spinnaker namespace hacks
  public String getRegion() {
    return getManifest().getNamespace();
  }

  public String getType() {
    return getManifest().getKind().toString();
  }

  public String getCloudProvider() {
    return KubernetesCloudProvider.getID();
  }

  public String getProviderType() {
    return KubernetesCloudProvider.getID();
  }

  abstract protected KubernetesManifest getManifest();
}
