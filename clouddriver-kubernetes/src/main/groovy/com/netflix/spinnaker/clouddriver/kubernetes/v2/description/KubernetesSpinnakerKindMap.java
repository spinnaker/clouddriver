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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSpinnakerKindMap {
  public enum SpinnakerKind {
    INSTANCES("instances"),
    CONFIGS("configs"),
    SERVER_GROUPS("serverGroups"),
    LOAD_BALANCERS("loadBalancers"),
    SECURITY_GROUPS("securityGroups"),
    SERVER_GROUP_MANAGERS("serverGroupManagers"),
    UNCLASSIFIED("unclassified");

    private final String id;

    SpinnakerKind(String id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return id;
    }

    @JsonCreator
    public static SpinnakerKind fromString(String name) {
      return Arrays.stream(values())
          .filter(k -> k.toString().equalsIgnoreCase(name))
          .findFirst()
          .orElse(UNCLASSIFIED);
    }
  }

  private final ImmutableMap<SpinnakerKind, ImmutableSet<KubernetesKind>> spinnakerToKubernetes;
  private final ImmutableMap<KubernetesKind, SpinnakerKind> kubernetesToSpinnaker;

  public KubernetesSpinnakerKindMap(List<KubernetesHandler> handlers) {
    ImmutableMap.Builder<KubernetesKind, SpinnakerKind> spinnakerToKubernetesBuilder =
        new ImmutableMap.Builder<>();
    Map<SpinnakerKind, ImmutableSet.Builder<KubernetesKind>> kubernetesToSpinnakerBuilder =
        new HashMap<>();
    for (KubernetesHandler handler : handlers) {
      SpinnakerKind spinnakerKind = handler.spinnakerKind();
      KubernetesKind kubernetesKind = handler.kind();
      kubernetesToSpinnakerBuilder
          .computeIfAbsent(spinnakerKind, k -> new ImmutableSet.Builder<>())
          .add(kubernetesKind);
      spinnakerToKubernetesBuilder.put(kubernetesKind, spinnakerKind);
    }
    this.kubernetesToSpinnaker = spinnakerToKubernetesBuilder.build();
    this.spinnakerToKubernetes =
        kubernetesToSpinnakerBuilder.entrySet().stream()
            .collect(toImmutableMap(Map.Entry::getKey, e -> e.getValue().build()));
  }

  public SpinnakerKind translateKubernetesKind(KubernetesKind kubernetesKind) {
    return kubernetesToSpinnaker.getOrDefault(kubernetesKind, SpinnakerKind.UNCLASSIFIED);
  }

  public ImmutableSet<KubernetesKind> translateSpinnakerKind(SpinnakerKind spinnakerKind) {
    return spinnakerToKubernetes.get(spinnakerKind);
  }

  public ImmutableSet<KubernetesKind> allKubernetesKinds() {
    return kubernetesToSpinnaker.keySet();
  }

  public Map<String, String> kubernetesToSpinnakerKindStringMap() {
    return kubernetesToSpinnaker.entrySet().stream()
        .filter(x -> !x.getKey().equals(KubernetesKind.NONE))
        .collect(Collectors.toMap(x -> x.getKey().toString(), x -> x.getValue().toString()));
  }
}
