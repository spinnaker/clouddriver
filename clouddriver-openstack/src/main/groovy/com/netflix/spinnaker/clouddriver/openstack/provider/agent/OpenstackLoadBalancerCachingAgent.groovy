/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Vip

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackLoadBalancerCachingAgent extends AbstractOpenstackCachingAgent {

  final ObjectMapper objectMapper

  Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns)
  ] as Set)

  String agentType = "${accountName}/${region}/${OpenstackLoadBalancerCachingAgent.simpleName}"

  OpenstackLoadBalancerCachingAgent(OpenstackNamedAccountCredentials account, String region,
                                    final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)

    List<LbPool> pools = clientProvider.getAllLoadBalancerPools(region)

    pools.collect { pool ->
      String vipId = pool.vipId
      //we could create VIP caching agent and get the VIP from there. We could.
      Vip vip = vipId ? clientProvider.getVip(region, vipId) : null

      //same for floating ips.
      FloatingIP ip = vipId ? clientProvider.getAssociatedFloatingIp(region, vipId) : null

      //same for health monitors
      Set<HealthMonitor> healthMonitors = pool.healthMonitors?.collect { healthId ->
        clientProvider.getHealthMonitor(region, healthId)
      }?.toSet()

      //subnets cached
      Map<String, Object> subnetMap = providerCache.get(SUBNETS.ns, Keys.getSubnetKey(pool.subnetId, region, accountName))?.attributes
      OpenstackSubnet subnet = subnetMap ? objectMapper.convertValue(subnetMap, OpenstackSubnet) : null

      //server groups cached
      String loadBalancerKey = Keys.getLoadBalancerKey(pool.id, accountName, region)
      Collection<String> filters = providerCache.filterIdentifiers(SERVER_GROUPS.ns, Keys.getServerGroupKey('*', accountName, region))
      Collection<Map<String, Object>> serverGroups = providerCache.getAll(SERVER_GROUPS.ns, filters, RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))?.attributes
      //TODO make object once Derek's cluster caching PR is merged
      Collection<Map<String, Object>> filteredServerGroups = serverGroups?.findAll { sg -> sg.loadBalancers?.find { lbkey -> lbkey == loadBalancerKey } }

      //create load balancer and relationships
      OpenstackLoadBalancer loadBalancer = OpenstackLoadBalancer.from(pool, vip, subnet, ip, healthMonitors, filteredServerGroups, accountName, region)
      cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
        attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
        filteredServerGroups?.each { sg ->
          //TODO test these
          relationships[SERVER_GROUPS.ns].add(Keys.getServerGroupKey(sg.name.toString(), accountName, region))
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}")

    cacheResultBuilder.build()
  }

}
