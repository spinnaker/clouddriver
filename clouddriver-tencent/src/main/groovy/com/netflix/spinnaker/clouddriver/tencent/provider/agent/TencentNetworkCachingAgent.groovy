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
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentNetworkDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.NETWORKS

@Slf4j
class TencentNetworkCachingAgent implements CachingAgent, AccountAware{
  final ObjectMapper objectMapper
  final String region
  final String accountName
  final TencentNamedAccountCredentials credentials
  final String providerName = TencentInfrastructureProvider.name

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(NETWORKS.ns)
  ] as Set


  TencentNetworkCachingAgent(
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

    def networks = loadNetworksAll()

    List<CacheData> data = networks.collect() { TencentNetworkDescription network ->
      Map<String, Object> attributes = [(NETWORKS.ns): network]
      new DefaultCacheData(Keys.getNetworkKey(network.vpcId, accountName, region),
        attributes,  [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(NETWORKS.ns): data])
  }

  private Set<TencentNetworkDescription> loadNetworksAll() {
    VirtualPrivateCloudClient vpcClient = new VirtualPrivateCloudClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def networkSet = vpcClient.getNetworksAll()  //vpc

    def networkDescriptionSet =  networkSet.collect {
      def networkDesc = new TencentNetworkDescription()
      networkDesc.vpcId = it.vpcId
      networkDesc.vpcName = it.vpcName
      networkDesc.cidrBlock = it.cidrBlock
      networkDesc.isDefault = it.isDefault
      networkDesc
    }
    return networkDescriptionSet
  }

}
