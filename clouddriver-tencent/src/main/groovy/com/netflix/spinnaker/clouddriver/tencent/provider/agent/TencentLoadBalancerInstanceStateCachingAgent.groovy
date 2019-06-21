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
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTargetHealth
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.HEALTH_CHECKS

@Slf4j
class TencentLoadBalancerInstanceStateCachingAgent implements CachingAgent, HealthProvidingCachingAgent, AccountAware{
  final String providerName = TencentInfrastructureProvider.name
  TencentNamedAccountCredentials credentials
  final String accountName
  final String region
  final ObjectMapper objectMapper
  final static String healthId = "tencent-load-balancer-instance-health"

  TencentLoadBalancerInstanceStateCachingAgent(TencentNamedAccountCredentials credentials,
                                               ObjectMapper objectMapper,
                                               String region) {
    this.credentials = credentials
    this.accountName = credentials.name
    this.region = region
    this.objectMapper = objectMapper
  }


  @Override
  String getHealthId() {
    healthId
  }

  @Override
  String getProviderName() {
    providerName
  }

  @Override
  String getAgentType() {
    return "$accountName/$region/${this.class.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter loadData in ${agentType}")

    def targetHealths = getLoadBalancerTargetHealth()
    Collection<String> evictions = providerCache.filterIdentifiers(HEALTH_CHECKS.ns, Keys.getTargetHealthKey('*', '*',
      '*', '*', accountName, region))

    List<CacheData> data = targetHealths.collect() { TencentLoadBalancerTargetHealth targetHealth ->
      Map<String, Object> attributes = ["targetHealth": targetHealth]
      def targetHealthKey = Keys.getTargetHealthKey(targetHealth.loadBalancerId, targetHealth.listenerId,
                                               targetHealth.locationId, targetHealth.instanceId, accountName, region)
      def keepKey = evictions.find {
        it.equals(targetHealthKey)
      }
      if (keepKey) {
        evictions.remove(keepKey)
      }
      new DefaultCacheData(targetHealthKey, attributes, [:])
    }

    log.info("Caching ${data.size()} items evictions ${evictions.size()} items in ${agentType}")
    new DefaultCacheResult([(HEALTH_CHECKS.ns): data], [(HEALTH_CHECKS.ns): evictions])
  }


  private List<TencentLoadBalancerTargetHealth> getLoadBalancerTargetHealth() {
    LoadBalancerClient client = new LoadBalancerClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def loadBalancerSet = client.getAllLoadBalancer()
    def loadBalancerIds = loadBalancerSet.collect {
      it.loadBalancerId
    }

    def totalLBCount = loadBalancerIds.size()
    log.info("Total loadBalancer Count ${totalLBCount}")

    def targetHealthSet = []
    if (totalLBCount > 0) {
      targetHealthSet = client.getLBTargetHealth(loadBalancerIds)
    }

    List<TencentLoadBalancerTargetHealth> tencentLBTargetHealths = []
    for (lbHealth in targetHealthSet) {
      def loadBalancerId = lbHealth.loadBalancerId
      def listenerHealths = lbHealth.listeners
      for (listenerHealth in listenerHealths) {
        def listenerId = listenerHealth.listenerId
        def ruleHealths = listenerHealth.rules
        def protocol = listenerHealth.protocol
        for (ruleHealth in ruleHealths) {
          def locationId = ''
          if (protocol == 'HTTP' || protocol == 'HTTPS') {
            locationId = ruleHealth.locationId
          }
          def instanceHealths = ruleHealth.targets
          for (instanceHealth in instanceHealths) {
            def targetId = instanceHealth.targetId
            def healthStatus = instanceHealth.healthStatus
            def port = instanceHealth.port
            def health = new TencentLoadBalancerTargetHealth(instanceId:targetId,
                        loadBalancerId:loadBalancerId, listenerId:listenerId, locationId:locationId,
                         healthStatus:healthStatus, port:port )
            tencentLBTargetHealths.add(health)
          }
        }
      }
    }

    return tencentLBTargetHealths
  }
}
