package com.netflix.spinnaker.clouddriver.tencent.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerCertificate
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerHealthCheck
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.moniker.Namer
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import groovy.util.logging.Slf4j
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.SERVER_GROUPS


@Slf4j
class TencentLoadBalancerCachingAgent implements OnDemandAgent, CachingAgent, AccountAware{
  final String accountName
  final String region
  final ObjectMapper objectMapper
  final String providerName = TencentInfrastructureProvider.name
  TencentNamedAccountCredentials credentials
  final OnDemandMetricsSupport metricsSupport
  final Namer<TencentBasicResource> namer
  String onDemandAgentType = "${agentType}-OnDemand"

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  TencentLoadBalancerCachingAgent(
    TencentNamedAccountCredentials credentials,
    ObjectMapper objectMapper,
    Registry registry,
    String region
  ) {
    this.credentials = credentials
    this.accountName = credentials.name
    this.region = region
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${TencentCloudProvider.ID}:${OnDemandAgent.OnDemandType.LoadBalancer}")
    this.namer = NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(credentials.name)
      .withResource(TencentBasicResource)
  }

  @Override
  String getAgentType() {
    return "$accountName/$region/${this.class.simpleName}"
  }

  @Override
  String getProviderName() {
    return providerName
  }

  @Override
  String getAccountName() {
    return accountName
  }

  List<TencentLoadBalancer> loadLoadBalancerData(String loadBalancerId = null) {
    LoadBalancerClient client = new LoadBalancerClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def lbSet = []
    if (loadBalancerId) {
      lbSet = client.getLoadBalancerById(loadBalancerId)
    } else {
      lbSet = client.getAllLoadBalancer()
    }

    def loadBanancerList =  lbSet.collect {
      TencentLoadBalancer loadBalancer = new TencentLoadBalancer()
      loadBalancer.region = region
      loadBalancer.accountName = accountName
      loadBalancer.name = it.loadBalancerName
      loadBalancer.loadBalancerName = it.loadBalancerName
      loadBalancer.id = it.loadBalancerId
      loadBalancer.loadBalancerId = it.loadBalancerId
      loadBalancer.loadBalancerType = it.loadBalancerType
      loadBalancer.vpcId = it.vpcId
      loadBalancer.subnetId = it.subnetId
      loadBalancer.createTime = it.createTime
      loadBalancer.loadBalacnerVips = it.loadBalancerVips.collect {
        def vip = new String(it)
        vip
      }
      loadBalancer.securityGroups = it.secureGroups.collect {
        def sg = new String(it)
        sg
      }

      def queryListeners = client.getAllLBListener(loadBalancer.id)
      def listenerIdList = queryListeners.collect {
        it.listenerId
      } as List<String>
      //all listener's targets
      def lbTargetList = []
      if (listenerIdList.size() > 0) {
        lbTargetList = client.getLBTargetList(loadBalancer.id, listenerIdList)
      }

      def listeners = queryListeners.collect {
        def listener = new TencentLoadBalancerListener()
        listener.listenerId = it.listenerId
        listener.protocol = it.protocol
        listener.port = it.port
        listener.scheduler = it.scheduler
        listener.sessionExpireTime = it.sessionExpireTime
        listener.sniSwitch = it.sniSwitch
        listener.listenerName = it.listenerName
        if (it.certificate != null) {        //listener.certificate
          listener.certificate = new TencentLoadBalancerCertificate()
          listener.certificate.sslMode = it.certificate.SSLMode
          listener.certificate.certId = it.certificate.certId
          listener.certificate.certCaId = it.certificate.certCaId
        }
        if (it.healthCheck != null) {      //listener healtch check
          listener.healthCheck = new TencentLoadBalancerHealthCheck()
          listener.healthCheck.healthSwitch = it.healthCheck.healthSwitch
          listener.healthCheck.timeOut = it.healthCheck.timeOut
          listener.healthCheck.intervalTime = it.healthCheck.intervalTime
          listener.healthCheck.healthNum = it.healthCheck.healthNum
          listener.healthCheck.unHealthNum = it.healthCheck.unHealthNum
          listener.healthCheck.httpCode = it.healthCheck.httpCode
          listener.healthCheck.httpCheckPath = it.healthCheck.httpCheckPath
          listener.healthCheck.httpCheckDomain = it.healthCheck.httpCheckDomain
          listener.healthCheck.httpCheckMethod = it.healthCheck.httpCheckMethod
        }
        //targets 4 layer
        //def lbTargets = client.getLBTargets(loadBalancer.loadBalancerId, listener.listenerId)
        def lbTargets = lbTargetList.findAll {
          it.listenerId.equals(listener.listenerId)
        }
        lbTargets.each { listenBackend ->
          listener.targets = listenBackend.Targets.collect { targetEntry ->
            if (targetEntry != null) {
              def target = new TencentLoadBalancerTarget()
              target.instanceId = targetEntry.instanceId
              target.port = targetEntry.port
              target.weight = targetEntry.weight
              target.type = targetEntry.type
              target
            }
          }
        }

        //rules
        def rules = it.rules.collect() {
          def rule = new TencentLoadBalancerRule()
          rule.locationId = it.locationId
          rule.domain = it.domain
          rule.url = it.url
          if (it.certificate != null) {               //rule.certificate
            rule.certificate = new TencentLoadBalancerCertificate()
            rule.certificate.sslMode = it.certificate.SSLMode
            rule.certificate.certId = it.certificate.certId
            rule.certificate.certCaId = it.certificate.certCaId
          }
          if (it.healthCheck != null) {            //rule healthCheck
            rule.healthCheck = new TencentLoadBalancerHealthCheck()
            rule.healthCheck.healthSwitch = it.healthCheck.healthSwitch
            rule.healthCheck.timeOut = it.healthCheck.timeOut
            rule.healthCheck.intervalTime = it.healthCheck.intervalTime
            rule.healthCheck.healthNum = it.healthCheck.healthNum
            rule.healthCheck.unHealthNum = it.healthCheck.unHealthNum
            rule.healthCheck.httpCode = it.healthCheck.httpCode
            rule.healthCheck.httpCheckPath = it.healthCheck.httpCheckPath
            rule.healthCheck.httpCheckDomain = it.healthCheck.httpCheckDomain
            rule.healthCheck.httpCheckMethod = it.healthCheck.httpCheckMethod
          }

          //rule targets 7Larer
          lbTargets.each { listenBackend ->
            for (ruleTarget in listenBackend.Rules) {
              if (ruleTarget.LocationId.equals(rule.locationId)) {
                rule.targets = ruleTarget.Targets.collect { ruleTargetEntry ->
                  def target = new TencentLoadBalancerTarget()
                  target.instanceId = ruleTargetEntry.instanceId
                  target.port = ruleTargetEntry.port
                  target.weight = ruleTargetEntry.weight
                  target.type = ruleTargetEntry.type
                  target
                }
              }
            }
          }
          rule
        }
        listener.rules = rules
        listener
      }
      loadBalancer.listeners = listeners
      loadBalancer
    }
    return loadBanancerList
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == TencentCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    log.info("Enter handle, data = ${data}")
    if (!data.containsKey("loadBalancerId") ||
      !data.containsKey("account") ||
      !data.containsKey("region")  ||
      accountName != data.account ||
      region != data.region) {
      return null
    }

    def loadBalancer =  metricsSupport.readData {
      loadLoadBalancerData(data.loadBalancerId as String)[0]
    }
    if (!loadBalancer) {
      log.info("Can not find loadBalancer ${data.loadBalancerId}")
      return null
    }

    def cacheResult = metricsSupport.transformData {
      buildCacheResult([loadBalancer], null, null)
    }

    def cacheResultAsJson = objectMapper.writeValueAsString(cacheResult.cacheResults)
    def loadBalancerKey = Keys.getLoadBalancerKey(data.loadBalancerId as String, accountName, region)
    if (cacheResult.cacheResults.values().flatten().empty) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems ON_DEMAND.ns, [loadBalancerKey]
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          loadBalancerKey,
          10 * 60,
          [
            cacheTime   : new Date(),
            cacheResults: cacheResultAsJson
          ],
          [:]
        )
        providerCache.putCacheData ON_DEMAND.ns, cacheData
      }
    }

    Map<String, Collection<String>> evictions = loadBalancer ? [:] : [
      (LOAD_BALANCERS.ns): [loadBalancerKey]]

    return new OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: cacheResult,
      evictions: evictions
    )
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter LoadBalancerCacheingAgent loadData ")

    def loadBalancerSet = loadLoadBalancerData()
    log.info("Total loadBanancre Number = ${loadBalancerSet.size()} in ${agentType}")
    def toEvictOnDemandCacheData = []
    def toKeepOnDemandCacheData = []

    Long start = System.currentTimeMillis()
    def loadBalancerKeys = loadBalancerSet.collect {
      Keys.getLoadBalancerKey(it.id, credentials.name, region)
    } as Set<String>

    def pendingOnDemandRequestKeys = providerCache
      .filterIdentifiers(
      ON_DEMAND.ns,
      Keys.getLoadBalancerKey("*", credentials.name, region))
      .findAll { loadBalancerKeys.contains(it) }

    def pendingOnDemandRequestsForloadBalancer = providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys)
    pendingOnDemandRequestsForloadBalancer.each {
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        toEvictOnDemandCacheData << it
      } else {
        toKeepOnDemandCacheData << it
      }
    }

    CacheResult result = buildCacheResult(loadBalancerSet, toKeepOnDemandCacheData, toEvictOnDemandCacheData)

    result.cacheResults[ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    /*
    result.cacheResults.each { String namespace, Collection<CacheData> caches->
      log.info "namespace $namespace"
      caches.each{
        log.info "attributes: $it.attributes, relationships: $it.relationships"
      }
    }*/
    return result
  }

  private CacheResult buildCacheResult(Collection<TencentLoadBalancer> loadBalancerSet,
                                       Collection<CacheData> toKeepOnDemandCacheData,
                                       Collection<CacheData> toEvictOnDemandCacheData) {
    log.info "Start build cache for $agentType"

    Map<String, Collection<CacheData>> cacheResults = [:]
    Map<String, Collection<String>> evictions = toEvictOnDemandCacheData ? [(ON_DEMAND.ns):toEvictOnDemandCacheData*.id] : [:]

    Map<String, Map<String, CacheData>> namespaceCache = [:].withDefault {
      namespace->[:].withDefault {id->new MutableCacheData(id as String)}
    }

    loadBalancerSet.each {
      Moniker moniker = namer.deriveMoniker it
      def applicationName = moniker.app
      if (applicationName == null) {
        return  //=continue
      }

      def loadBalancerKey = Keys.getLoadBalancerKey(it.id, accountName, region)
      def appKey = Keys.getApplicationKey(applicationName)
      //List<String> instanceKeys = []

      // application
      def applications = namespaceCache[APPLICATIONS.ns]
      applications[appKey].attributes.name = applicationName
      applications[appKey].relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)

      // compare onDemand
      //def onDemandLoadBalancerCache = toKeepOnDemandCacheData.find {
      //  it.id == loadBalancerKey
      //}
      def onDemandLoadBalancerCache = false
      if (onDemandLoadBalancerCache) {
        //mergeOnDemandCache(onDemandLoadBalancerCache, namespaceCache)
      } else {
        // LoadBalancer
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.application = applicationName
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.name = it.name
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.region = it.region
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.id = it.id
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.loadBalancerId = it.loadBalancerId
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.accountName = accountName
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.vpcId = it.vpcId
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.subnetId = it.subnetId
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.loadBalancerType = it.loadBalancerType
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.createTime = it.createTime
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.loadBalacnerVips = []
        it.loadBalacnerVips.each {
          def vip = new String(it)
          namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.loadBalacnerVips.add(vip)
        }
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.securityGroups = []
        it.securityGroups.each {
          def sg = new String(it)
          namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.securityGroups.add(sg)
        }
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.listeners = [] as List<TencentLoadBalancerListener>
        it.listeners.each {
          def listener = new TencentLoadBalancerListener()
          listener.copyListener(it)
          namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].attributes.listeners.add(listener)
        }
        namespaceCache[LOAD_BALANCERS.ns][loadBalancerKey].relationships[APPLICATIONS.ns].add(appKey)
      }
    }

    namespaceCache.each {String namespace, Map<String, CacheData> cacheDataMap ->
      cacheResults[namespace] = cacheDataMap.values()
    }
    cacheResults[ON_DEMAND.ns] = toKeepOnDemandCacheData

    CacheResult result = new DefaultCacheResult(
      cacheResults, evictions
    )
    result
  }

  def mergeOnDemandCache(CacheData onDemandLoadBalancerCache, Map<String, Map<String, CacheData>> namespaceCache) {
    Map<String, List<MutableCacheData>> onDemandCache = objectMapper.readValue(
      onDemandLoadBalancerCache.attributes.cacheResults as String,
      new TypeReference<Map<String, List<MutableCacheData>>>() {})

    onDemandCache.each { String namespace, List<MutableCacheData> cacheDataList ->
      if (namespace != 'onDemand') {
        cacheDataList.each {
          def existingCacheData = namespaceCache[namespace][it.id]
          if (!existingCacheData) {
            namespaceCache[namespace][it.id] = it
          } else {
            existingCacheData.attributes.putAll(it.attributes)
            it.relationships.each { String relationshipName, Collection<String> relationships ->
              existingCacheData.relationships[relationshipName].addAll(relationships)
            }
          }
        }
      }
    }
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }


}
