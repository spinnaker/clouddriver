/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ParametersAreNonnullByDefault
public class GlobalResourcePropertyRegistry implements ResourcePropertyRegistry {
  private final ImmutableMap<KubernetesKind, KubernetesResourceProperties> globalProperties;

  @Autowired
  public GlobalResourcePropertyRegistry(
      List<KubernetesHandler> handlers, KubernetesSpinnakerKindMap kindMap) {
    for (KubernetesHandler handler : handlers) {
      kindMap.addRelationship(handler.spinnakerKind(), handler.kind());
    }
    this.globalProperties =
        handlers.stream()
            .collect(
                toImmutableMap(
                    KubernetesHandler::kind,
                    h -> new KubernetesResourceProperties(h, h.versioned())));
  }

  @Override
  @Nonnull
  public KubernetesResourceProperties get(KubernetesKind kind) {
    KubernetesResourceProperties globalResult = globalProperties.get(kind);
    if (globalResult != null) {
      return globalResult;
    }

    return globalProperties.get(KubernetesKind.NONE);
  }

  @Override
  @Nonnull
  public Collection<KubernetesResourceProperties> values() {
    return globalProperties.values();
  }
}
