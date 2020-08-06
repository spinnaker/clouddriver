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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistryUtils {

  public static void removeSensitiveKeys(
      ResourcePropertyRegistry propertyRegistry, KubernetesManifest manifest) {
    propertyRegistry.get(manifest.getKind()).getHandler().removeSensitiveKeys(manifest);
  }

  public static void addRelationships(
      ResourcePropertyRegistry propertyRegistry,
      @Nonnull KubernetesKind kind,
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    propertyRegistry.get(kind).getHandler().addRelationships(allResources, relationshipMap);
  }
}
