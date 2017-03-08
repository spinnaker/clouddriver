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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceAggregatedList
import com.google.api.services.compute.model.InstancesScopedList
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.consul.model.ConsulHealth
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS

@Slf4j
@InheritConstructors
class GoogleInstanceCachingAgent extends AbstractGoogleCachingAgent {
  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(INSTANCES.ns),
      INFORMATIVE.forType(SERVER_GROUPS.ns),
  ]

  String agentType = "${accountName}/global/${GoogleInstanceCachingAgent.simpleName}"

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleInstance> instances = getInstances()
    buildCacheResults(providerCache, instances)
  }

  List<GoogleInstance> getInstances() {
    List<GoogleInstance> instances = new ArrayList<GoogleInstance>()
    String pageToken = null

    while (true) {
      InstanceAggregatedList instanceAggregatedList = timeExecute(
          compute.instances().aggregatedList(project).setPageToken(pageToken),
          "compute.instances.aggregatedList",
          TAG_SCOPE, SCOPE_GLOBAL)

      instances += transformInstances(instanceAggregatedList)
      pageToken = instanceAggregatedList.getNextPageToken()

      if (!pageToken) {
        break
      }
    }

    return instances
  }

  CacheResult buildCacheResults(ProviderCache providerCache, List<GoogleInstance> googleInstances) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    googleInstances.each { GoogleInstance instance ->
      def instanceKey = Keys.getInstanceKey(accountName, instance.region, instance.name)
      cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
        attributes = objectMapper.convertValue(instance, ATTRIBUTES)
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instances in ${agentType}")

    cacheResultBuilder.build()
  }

  List<GoogleInstance> transformInstances(InstanceAggregatedList instanceAggregatedList) throws IOException {
    List<GoogleInstance> instances = []

    instanceAggregatedList?.items?.each { String zone, InstancesScopedList instancesScopedList ->
      def localZoneName = Utils.getLocalName(zone)
      instancesScopedList?.instances?.each { Instance instance ->
        def consulNode = credentials.consulConfig?.enabled ?
          ConsulProviderUtils.getHealths(credentials.consulConfig, instance.getName())
          : null
        long instanceTimestamp = instance.creationTimestamp ?
            Utils.getTimeFromTimestamp(instance.creationTimestamp) :
            Long.MAX_VALUE
        String instanceName = Utils.getLocalName(instance.name)
        def googleInstance = new GoogleInstance(
            name: instanceName,
            instanceType: Utils.getLocalName(instance.machineType),
            launchTime: instanceTimestamp,
            zone: localZoneName,
            region: credentials.regionFromZone(localZoneName),
            networkInterfaces: instance.networkInterfaces,
            metadata: instance.metadata,
            disks: instance.disks,
            serviceAccounts: instance.serviceAccounts,
            selfLink: instance.selfLink,
            tags: instance.tags,
            labels: instance.labels,
            consulNode: consulNode,
            instanceHealth: new GoogleInstanceHealth(
                status: GoogleInstanceHealth.Status.valueOf(instance.getStatus())
            ))
        instances << googleInstance
      }
    }

    return instances
  }

}
