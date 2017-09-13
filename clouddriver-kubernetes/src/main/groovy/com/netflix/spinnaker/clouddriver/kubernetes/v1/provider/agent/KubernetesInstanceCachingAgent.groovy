/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.Pod

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class KubernetesInstanceCachingAgent extends KubernetesCachingAgent<KubernetesV1Credentials> {
  static final String CACHE_TTL_ANNOTATION = "cache.spinnaker.io/ttl"

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  KubernetesInstanceCachingAgent(KubernetesNamedAccountCredentials<KubernetesV1Credentials> namedAccountCredentials,
                                 ObjectMapper objectMapper,
                                 Registry registry,
                                 int agentIndex,
                                 int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading pods in $agentType")
    reloadNamespaces()

    def pods = namespaces.collect { String namespace ->
      credentials.apiAdaptor.getPods(namespace)
    }.flatten()

    buildCacheResult(pods)
  }

  private CacheResult buildCacheResult(List<Pod> pods) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    Map<String, Map<String, List<Event>>>  podEvents = [:].withDefault { _ -> [:] }
    try {
      namespaces.each { String namespace ->
        podEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, "Pod")
      }
    } catch (Exception e) {
      log.warn "Failure fetching events for all pods in $namespaces", e
    }

    for (Pod pod : pods) {
      if (!pod) {
        continue
      }

      def events = podEvents[pod.metadata.namespace][pod.metadata.name] ?: []

      def key = Keys.getInstanceKey(accountName, pod.metadata.namespace, pod.metadata.name)
      cachedInstances[key].with {
        if (pod.metadata?.annotations?.containsKey(CACHE_TTL_ANNOTATION)) {
          attributes.cacheExpiry = pod.metadata.annotations[CACHE_TTL_ANNOTATION]
        }
        attributes.name = pod.metadata.name
        attributes.instance = new KubernetesInstance(pod, events)
      }

    }

    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
    ], [:])
  }
}
