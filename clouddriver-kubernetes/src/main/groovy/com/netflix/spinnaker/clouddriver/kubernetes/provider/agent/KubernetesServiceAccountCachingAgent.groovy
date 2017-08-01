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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.ServiceAccount

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class KubernetesServiceAccountCachingAgent extends KubernetesCachingAgent {
  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.SERVICE_ACCOUNTS.ns),
  ] as Set)

  KubernetesServiceAccountCachingAgent(String accountName,
                                 KubernetesCredentials credentials,
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
    log.info("Loading service accounts in $agentType")
    reloadNamespaces()

    def serviceAccounts = namespaces.collect { String namespace ->
      credentials.apiAdaptor.getServiceAccounts(namespace)
    }.flatten()

    buildCacheResult(serviceAccounts)
  }

  private CacheResult buildCacheResult(List<ServiceAccount> serviceAccounts) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedServiceAccounts = MutableCacheData.mutableCacheMap()

    for (ServiceAccount sa : serviceAccounts) {
      if (!sa) {
        continue
      }

      def key = Keys.getServiceAccountKey(accountName, sa.metadata.namespace, sa.metadata.name)

      cachedServiceAccounts[key].with {
        attributes.name = sa.metadata.name
        attributes.namespace = sa.metadata.namespace
      }

    }

    log.info("Caching ${cachedServiceAccounts.size()} service accounts in ${agentType}")

    new DefaultCacheResult([
        (Keys.Namespace.SERVICE_ACCOUNTS.ns): cachedServiceAccounts.values(),
    ], [:])
  }

  @Override
  String getSimpleName() {
    KubernetesServiceAccountCachingAgent.simpleName
  }
}
