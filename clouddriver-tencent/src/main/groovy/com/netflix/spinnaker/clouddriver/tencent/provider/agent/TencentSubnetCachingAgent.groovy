package com.netflix.spinnaker.clouddriver.tencent.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSubnetDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.SUBNETS

@Slf4j
class TencentSubnetCachingAgent implements CachingAgent, AccountAware {
  final ObjectMapper objectMapper
  final String region
  final String accountName
  final TencentNamedAccountCredentials credentials
  final String providerName = TencentInfrastructureProvider.name

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SUBNETS.ns)
  ] as Set


  TencentSubnetCachingAgent(
    TencentNamedAccountCredentials creds,
    ObjectMapper objectMapper,
    String region
  ) {
    this.accountName = creds.name
    this.credentials = creds
    this.objectMapper = objectMapper
    this.region = region
  }

  @Override
  String getAgentType() {
    return "$accountName/$region/${this.class.simpleName}"
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def subnets = loadSubnetsAll()

    List<CacheData> data = subnets.collect() { TencentSubnetDescription subnet ->
      Map<String, Object> attributes = [(SUBNETS.ns): subnet]
      new DefaultCacheData(Keys.getSubnetKey(subnet.subnetId, accountName, region),
        attributes,  [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(SUBNETS.ns): data])
  }

  private Set<TencentSubnetDescription> loadSubnetsAll() {
    VirtualPrivateCloudClient vpcClient = new VirtualPrivateCloudClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def subnetSet = vpcClient.getSubnetsAll()

    def subnetDescriptionSet =  subnetSet.collect {
      def subnetDesc = new TencentSubnetDescription()
      subnetDesc.subnetId = it.subnetId
      subnetDesc.vpcId = it.vpcId
      subnetDesc.subnetName = it.subnetName
      subnetDesc.cidrBlock = it.cidrBlock
      subnetDesc.isDefault = it.isDefault
      subnetDesc.zone = it.zone
      subnetDesc
    }
    return subnetDescriptionSet
  }

}
