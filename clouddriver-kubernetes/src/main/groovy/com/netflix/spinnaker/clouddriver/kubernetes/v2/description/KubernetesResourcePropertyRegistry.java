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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesResourcePropertyRegistry {
  private final ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> globalProperties =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<
          String, ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties>>
      accountProperties = new ConcurrentHashMap<>();

  @Autowired
  public KubernetesResourcePropertyRegistry(
      List<KubernetesHandler> handlers, KubernetesSpinnakerKindMap kindMap) {
    for (KubernetesHandler handler : handlers) {
      KubernetesResourceProperties properties =
          KubernetesResourceProperties.builder()
              .handler(handler)
              .versioned(handler.versioned())
              .versionedConverter(new KubernetesVersionedArtifactConverter())
              .unversionedConverter(new KubernetesUnversionedArtifactConverter())
              .build();

      kindMap.addRelationship(handler.spinnakerKind(), handler.kind());
      globalProperties.put(handler.kind(), properties);
    }
  }

  @Nonnull
  public KubernetesResourceProperties get(String account, KubernetesKind kind) {
    KubernetesResourceProperties accountResult =
        accountProperties.getOrDefault(account, new ConcurrentHashMap<>()).get(kind);
    if (accountResult != null) {
      return accountResult;
    }

    KubernetesResourceProperties globalResult = globalProperties.get(kind);
    if (globalResult != null) {
      return globalResult;
    }

    return globalProperties.get(KubernetesKind.NONE);
  }

  public synchronized void registerAccountProperty(
      String account, KubernetesResourceProperties properties) {
    ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> propertyMap =
        accountProperties.computeIfAbsent(account, a -> new ConcurrentHashMap<>());
    propertyMap.put(properties.getHandler().kind(), properties);
  }

  public Collection<KubernetesResourceProperties> values() {
    Collection<KubernetesResourceProperties> result = new ArrayList<>(globalProperties.values());
    result.addAll(
        accountProperties.values().stream()
            .map(ConcurrentHashMap::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));

    return result;
  }
}
