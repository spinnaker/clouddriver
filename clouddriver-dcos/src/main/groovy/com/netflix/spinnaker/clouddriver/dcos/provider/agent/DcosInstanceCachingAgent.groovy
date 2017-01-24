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

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.Task

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class DcosInstanceCachingAgent implements CachingAgent, AccountAware {

  private final String accountName
  private final DcosCredentials credentials
  private final DCOS dcosClient
  private final DcosCloudProvider dcosCloudProvider = new DcosCloudProvider()
  private final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
          AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  DcosInstanceCachingAgent(String accountName,
                           DcosCredentials credentials,
                           DcosClientProvider clientProvider,
                           ObjectMapper objectMapper) {
    this.credentials = credentials
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(credentials)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  String getAccountName() {
    return accountName
  }

  @Override
  String getAgentType() {
    //return "${accountName}/${getSimpleName()}[${agentIndex + 1}/$agentCount]"
    "${accountName}/${DcosInstanceCachingAgent.simpleName}"
  }
//
//  @Override
//  String getOnDemandAgentType() {
//    "${getAgentType()}-OnDemand"
//  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading tasks in $agentType")

    // TODO handle failure
    def tasks = dcosClient.getTasks().tasks

    buildCacheResult(tasks)
  }

  private CacheResult buildCacheResult(List<Task> tasks) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    for (Task task : tasks) {
      if (!task) {
        continue
      }

      def spinnakerId = DcosSpinnakerId.parse(task.appId)
      if (spinnakerId == null) {
        continue
      }

      def key = Keys.getInstanceKey(spinnakerId.account, spinnakerId.group, task.id)
      cachedInstances[key].with {
        attributes.name = task.id
        attributes.instance = new DcosInstance(task)
      }
    }

    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
            (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
    ], [:])
  }
}
