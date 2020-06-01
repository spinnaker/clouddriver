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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesCoreCachingAgent extends KubernetesV2OnDemandCachingAgent {
  public KubernetesCoreCachingAgent(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount, agentInterval);
  }

  public Collection<AgentDataType> getProvidedDataTypes() {
    Stream<String> logicalTypes =
        Stream.of(Keys.LogicalKind.APPLICATIONS, Keys.LogicalKind.CLUSTERS, Keys.Kind.ARTIFACT)
            .map(Enum::toString);
    Stream<String> kubernetesTypes = primaryKinds().stream().map(KubernetesKind::toString);

    return Stream.concat(logicalTypes, kubernetesTypes)
        .map(AUTHORITATIVE::forType)
        .collect(toImmutableSet());
  }

  @Override
  protected List<KubernetesKind> primaryKinds() {
    return credentials.getGlobalKinds();
  }

  /**
   * See the comment on the super method for more details about why this function exists.
   *
   * <p>As noted there, we want the KubernetesCoreCachingAgent to handle all requests for pending
   * on-demand cache refreshes to avoid duplicate work. But as users can have multiple cache threads
   * for a given account, there may be multiple KubernetesCoreCachingAgent's for a single account,
   * all of which would duplicate work if we sent the work to all of them.
   *
   * <p>In order to minimize duplicate work, we'll elect the agent with index 0 to handle all of
   * these requests, and have it return all pending on-demand refresh requests (not just ones
   * related to its slice of namespaces).
   */
  protected boolean handlePendingOnDemandRequests() {
    return agentIndex == 0;
  }
}
