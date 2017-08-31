/*
 * Copyright 2017 Skuid, Inc.
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
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.ConfigMap

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class KubernetesConfigMapCachingAgent extends KubernetesCachingAgent {
  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.CONFIG_MAPS.ns),
  ] as Set)

  KubernetesConfigMapCachingAgent(String accountName,
                                  KubernetesV1Credentials credentials,
                                  ObjectMapper objectMapper,
                                  int agentIndex,
                                  int agentCount) {
    super(accountName, objectMapper, credentials, agentIndex, agentCount)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading config maps in $agentType")
    reloadNamespaces()

    def configMaps = namespaces.collect { String namespace ->
      credentials.apiAdaptor.getConfigMaps(namespace)
    }.flatten()

    buildCacheResult(configMaps)
  }

  private CacheResult buildCacheResult(List<ConfigMap> configMaps) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedConfigMaps = MutableCacheData.mutableCacheMap()

    for (ConfigMap cm : configMaps) {
      if (!cm) {
        continue
      }

      def key = Keys.getConfigMapKey(accountName, cm.metadata.namespace, cm.metadata.name)

      cachedConfigMaps[key].with {
        attributes.name = cm.metadata.name
        attributes.namespace = cm.metadata.namespace
      }

    }

    log.info("Caching ${cachedConfigMaps.size()} configmaps in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.CONFIG_MAPS.ns): cachedConfigMaps.values(),
    ], [:])
  }

  @Override
  String getSimpleName() {
    KubernetesConfigMapCachingAgent.simpleName
  }
}
