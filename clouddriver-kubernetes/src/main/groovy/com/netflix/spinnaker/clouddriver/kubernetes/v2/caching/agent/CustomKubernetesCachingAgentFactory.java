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
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

public class CustomKubernetesCachingAgentFactory {
  public static KubernetesV2OnDemandCachingAgent create(
      KubernetesKind kind,
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount
  ) {
    return new Agent(
        kind,
        namedAccountCredentials,
        objectMapper,
        registry,
        agentIndex,
        agentCount
    );
  }

  private static class Agent extends KubernetesV2OnDemandCachingAgent {
    private final KubernetesKind kind;

    Agent(
        KubernetesKind kind,
        KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
        ObjectMapper objectMapper,
        Registry registry,
        int agentIndex,
        int agentCount
    ) {
      super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
      this.kind = kind;
    }

    @Override
    protected KubernetesKind primaryKind() {
      return this.kind;
    }

    @Override
    final public Collection<AgentDataType> getProvidedDataTypes() {
      return Collections.unmodifiableSet(
          new HashSet<>(Collections.singletonList(
              AUTHORITATIVE.forType(this.kind.toString())
          ))
      );
    }

    @Override
    public String getAgentType() {
      return String.format("%s/CustomKubernetes(%s)[%d/%d]", accountName, kind, agentIndex + 1, agentCount);
    }
  }
}
