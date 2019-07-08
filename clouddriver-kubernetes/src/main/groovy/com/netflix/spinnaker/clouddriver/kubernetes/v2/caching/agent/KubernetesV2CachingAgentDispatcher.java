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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.NONE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2CachingAgentDispatcher implements KubernetesCachingAgentDispatcher {
  @Autowired private ObjectMapper objectMapper;

  @Autowired private Registry registry;

  @Autowired private KubernetesResourcePropertyRegistry propertyRegistry;

  @Override
  public Collection<KubernetesCachingAgent> buildAllCachingAgents(
      KubernetesNamedAccountCredentials credentials) {
    KubernetesV2Credentials v2Credentials = (KubernetesV2Credentials) credentials.getCredentials();
    List<KubernetesCachingAgent> result = new ArrayList<>();
    Long agentInterval =
        Optional.ofNullable(credentials.getCacheIntervalSeconds())
            .map(TimeUnit.SECONDS::toMillis)
            .orElse(null);

    IntStream.range(0, credentials.getCacheThreads())
        .boxed()
        .forEach(
            i ->
                propertyRegistry.values().stream()
                    .map(KubernetesResourceProperties::getHandler)
                    .filter(Objects::nonNull)
                    .filter(h -> v2Credentials.isValidKind(h.kind()) || h.kind().equals(NONE))
                    .map(
                        h ->
                            h.buildCachingAgent(
                                credentials,
                                propertyRegistry,
                                objectMapper,
                                registry,
                                i,
                                credentials.getCacheThreads(),
                                agentInterval))
                    .filter(Objects::nonNull)
                    .forEach(c -> result.add((KubernetesCachingAgent) c)));

    if (v2Credentials.isMetricsComputed()) {
      IntStream.range(0, credentials.getCacheThreads())
          .boxed()
          .forEach(
              i ->
                  result.add(
                      new KubernetesMetricCachingAgent(
                          credentials,
                          objectMapper,
                          registry,
                          i,
                          credentials.getCacheThreads(),
                          agentInterval)));
    }

    return result.stream()
        .collect(Collectors.toMap(KubernetesCachingAgent::getAgentType, c -> c, (a, b) -> b))
        .values();
  }
}
