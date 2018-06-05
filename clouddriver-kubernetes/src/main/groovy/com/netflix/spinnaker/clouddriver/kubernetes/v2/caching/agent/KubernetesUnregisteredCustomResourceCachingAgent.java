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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

@Slf4j
public class KubernetesUnregisteredCustomResourceCachingAgent extends KubernetesV2OnDemandCachingAgent {
  KubernetesUnregisteredCustomResourceCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubernetesResourcePropertyRegistry propertyRegistry,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, propertyRegistry, objectMapper, registry, agentIndex, agentCount);

    this.liveCrdSupplier = Suppliers.memoizeWithExpiration(() -> credentials.list(KubernetesKind.CUSTOM_RESOURCE_DEFINITION, "")
        .stream()
        .map(c -> {
          Map<String, Object> spec = (Map) c.getOrDefault("spec", new HashMap<>());
          String scope = (String) spec.getOrDefault("scope", "");
          Map<String, String> names = (Map) spec.getOrDefault("names", new HashMap<>());
          String name = names.get("kind");

          return KubernetesKind.fromString(name, false, scope.equalsIgnoreCase("namespaced"));
        })
        .collect(Collectors.toList()), crdExpirySeconds, TimeUnit.SECONDS);
  }

  // TODO(lwander) make configurable
  private final static int crdExpirySeconds = 30;

  private final com.google.common.base.Supplier<List<KubernetesKind>> liveCrdSupplier;

  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableSet(
        primaryKinds().stream().map(k -> AUTHORITATIVE.forType(k.toString())).collect(Collectors.toSet())
    );
  }

  @Override
  protected List<KubernetesKind> primaryKinds() {
    try {
      return liveCrdSupplier.get();
    } catch (KubectlException e) {
      return new ArrayList<>();
    }
  }
}
