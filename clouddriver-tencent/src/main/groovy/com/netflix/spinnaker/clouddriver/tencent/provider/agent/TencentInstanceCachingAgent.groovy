package com.netflix.spinnaker.clouddriver.tencent.provider.agent


import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstanceHealth
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*

@Slf4j
@InheritConstructors
class TencentInstanceCachingAgent extends AbstractTencentCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(CLUSTERS.ns)
  ] as Set

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    // first, find all auto scaling instances
    // second, get detail info of below instances
    log.info "start load auto scaling instance data"

    Map<String, Collection<CacheData>> cacheResults = [:]
    Map<String, Map<String, CacheData>> namespaceCache = [:].withDefault {
      namespace -> [:].withDefault { id -> new MutableCacheData(id as String) }
    }

    AutoScalingClient asClient = new AutoScalingClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )
    CloudVirtualMachineClient cvmClient = new CloudVirtualMachineClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def asgInstances = asClient.getAutoScalingInstances()
    def asgInstanceIds = asgInstances.collect {
      it.instanceId
    }

    log.info "loads ${asgInstanceIds.size()} auto scaling instances. "

    log.info "start load instances detail info."
    def result = cvmClient.getInstances asgInstanceIds

    result.each {
      def launchTime = CloudVirtualMachineClient.ConvertIsoDateTime it.createdTime
      def launchConfigurationName = asgInstances.find { asgIns ->
        asgIns.instanceId == it.instanceId
      }?.launchConfigurationName

      def serverGroupName = it.tags.find {
        it.key == AutoScalingClient.defaultServerGroupTagKey
      }?.value

      def tencentInstance = new TencentInstance(
        account: accountName,
        name: it.instanceId,
        instanceName: it.instanceName,
        launchTime: launchTime ? launchTime.time : 0,
        zone: it.placement.zone,
        vpcId: it.virtualPrivateCloud.vpcId,
        subnetId: it.virtualPrivateCloud.subnetId,
        imageId: it.imageId,
        instanceType: it.instanceType,
        securityGroupIds: it.securityGroupIds,
        instanceHealth: new TencentInstanceHealth(instanceStatus: it.instanceState),
        serverGroupName: serverGroupName?:launchConfigurationName
        // if default tag is invalid, use launchConfigurationName
        // launchConfigurationName is the same with autoScalingGroupName
      )

      if (it.tags) {
        it.tags.each { tag->
          tencentInstance.tags.add(["key": tag.key, "value": tag.value])
        }
      }

      def instances = namespaceCache[INSTANCES.ns]
      def instanceKey = Keys.getInstanceKey it.instanceId, this.accountName, this.region

      instances[instanceKey].attributes.instance = tencentInstance

      def moniker = tencentInstance.moniker
      if (moniker) {
        def clusterKey = Keys.getClusterKey moniker.cluster, moniker.app, accountName
        def serverGroupKey = Keys.getServerGroupKey tencentInstance.serverGroupName, accountName, region
        instances[instanceKey].relationships[CLUSTERS.ns].add clusterKey
        instances[instanceKey].relationships[SERVER_GROUPS.ns].add serverGroupKey
      }
      null
    }
    namespaceCache.each { String namespace, Map<String, CacheData> cacheDataMap ->
      cacheResults[namespace] = cacheDataMap.values()
    }

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults)
    log.info 'finish loads instance data.'
    log.info "Caching ${namespaceCache[INSTANCES.ns].size()} items in $agentType"
    defaultCacheResult
  }
}
