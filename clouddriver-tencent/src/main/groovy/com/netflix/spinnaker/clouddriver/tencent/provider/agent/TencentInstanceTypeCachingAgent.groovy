package com.netflix.spinnaker.clouddriver.tencent.provider.agent


import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstanceType
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.INSTANCE_TYPES

@Slf4j
@InheritConstructors
class TencentInstanceTypeCachingAgent extends AbstractTencentCachingAgent {
  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(INSTANCE_TYPES.ns)
  ] as Set

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info "start load instance types data"

    Map<String, Collection<CacheData>> cacheResults = [:]
    Map<String, Map<String, CacheData>> namespaceCache = [:].withDefault {
      namespace -> [:].withDefault { id -> new MutableCacheData(id as String) }
    }

    CloudVirtualMachineClient cvmClient = new CloudVirtualMachineClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region,
    )

    def result = cvmClient.getInstanceTypes()
    result.each {
      def tencentInstanceType = new TencentInstanceType(
        name: it.instanceType,
        account: this.accountName,
        region: this.region,
        zone: it.zone,
        instanceFamily: it.instanceFamily,
        cpu: it.getCPU(),
        mem: it.getMemory()
      )

      def instanceTypes = namespaceCache[INSTANCE_TYPES.ns]
      def instanceTypeKey = Keys.getInstanceTypeKey this.accountName, this.region, tencentInstanceType.name

      instanceTypes[instanceTypeKey].attributes.instanceType = tencentInstanceType
      null
    }

    namespaceCache.each { String namespace, Map<String, CacheData> cacheDataMap ->
      cacheResults[namespace] = cacheDataMap.values()
    }

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults)
    log.info "finish loads instance type data."
    log.info "Caching ${namespaceCache[INSTANCE_TYPES.ns].size()} items in $agentType"
    defaultCacheResult
  }
}
