

package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.SERVER_GROUPS


@Slf4j
@Component
class TencentLoadBalancerProvider implements LoadBalancerProvider<TencentLoadBalancer> {

  final String cloudProvider = TencentCloudProvider.ID

  private final Cache cacheView
  final ObjectMapper objectMapper
  private final TencentInfrastructureProvider tencentProvider

  @Autowired
  public TencentLoadBalancerProvider(Cache cacheView, TencentInfrastructureProvider tProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.tencentProvider = tProvider
    this.objectMapper = objectMapper
  }

  @Override
  Set<TencentLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    log.info("Enter tencent getApplicationLoadBalancers " + applicationName)

    CacheData application = cacheView.get(
      APPLICATIONS.ns,
      Keys.getApplicationKey(applicationName),
      RelationshipCacheFilter.include(LOAD_BALANCERS.ns)
    )

    if (application) {
      def loadBalancerKeys = application.relationships[LOAD_BALANCERS.ns]
      if (loadBalancerKeys) {
        Collection<CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys)
        def loadBalancerSet = translateLoadBalancersFromCacheData(loadBalancers)
        return loadBalancerSet
      }
    }else {
      null
    }
  }

  Set<TencentLoadBalancer> getAll() {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey("*","*","*"))
  }

  Set<TencentLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    log.info("Enter getAllMatchingKeyPattern patten = " + pattern)
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns, pattern))
  }

  Set<TencentLoadBalancer> loadResults(Collection<String> identifiers) {
    log.info("Enter loadResults id = " + identifiers)
    def data = cacheView.getAll(Keys.Namespace.LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)
    return transformed
  }

  private Set<LoadBalancerInstance> getLoadBalancerInstanceByListenerId(TencentLoadBalancer loadBalancer, String listenerId) {
    TencentLoadBalancerListener listener = loadBalancer.listeners.find {
      it.listenerId.equals(listenerId)
    }

    Set<LoadBalancerInstance> instances = []
    if (listener) {
      listener.targets.each {
        def instance = new LoadBalancerInstance(id: it.instanceId)
        instances.add(instance)
      }
      listener.rules.each { rule ->
        rule.targets.each {
          def instance = new LoadBalancerInstance(id: it.instanceId)
          instances.add(instance)
        }
      }
    }
    return instances
  }

  private LoadBalancerServerGroup getLoadBalancerServerGroup(CacheData loadBalancerCache, TencentLoadBalancer loadBalancerDesc) {
    def serverGroupKeys = loadBalancerCache.relationships[SERVER_GROUPS.ns]
    if (serverGroupKeys) {
      def serverGroupKey = serverGroupKeys[0]
      if (serverGroupKey) {
        log.info("loadBalancer ${loadBalancerDesc.loadBalancerId} bind serverGroup ${serverGroupKey}")
        def parts = Keys.parse(serverGroupKey)
        def lbServerGroup = new LoadBalancerServerGroup(name: parts.name, account: parts.account, region: parts.region)
        def serverGroup = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
        if (serverGroup) {
          def asgInfo = serverGroup?.attributes?.asg as Map
          def lbInfo = asgInfo?.get("forwardLoadBalancerSet") as List
          if (lbInfo) {
            //def lbId = lbInfo[0]["loadBalancerId"] as String
            def listenerId = lbInfo[0]["listenerId"] as String
            log.info("loadBalancer ${loadBalancerDesc.loadBalancerId} listener ${listenerId} bind serverGroup ${serverGroupKey}")
            if (listenerId?.size() > 0) {
              lbServerGroup.instances = getLoadBalancerInstanceByListenerId(loadBalancerDesc, listenerId)
            }
          }
        }
        return lbServerGroup
      }
    }
    null
  }

  TencentLoadBalancer fromCacheData(CacheData cacheData) {
    //log.info("Enter formCacheDate data = $cacheData.attributes")
    TencentLoadBalancer loadBalancerDescription = objectMapper.convertValue(cacheData.attributes, TencentLoadBalancer)
    def serverGroup = getLoadBalancerServerGroup(cacheData, loadBalancerDescription)
    if (serverGroup) {
      loadBalancerDescription.serverGroups.add(serverGroup)
    }
    return loadBalancerDescription
  }

  @Override
  List<TencentLoadBalancerDetail> byAccountAndRegionAndName(String account, String region, String id) {
    log.info("Get loadBalancer byAccountAndRegionAndName: account=${account},region=${region},id=${id}")
    def lbKey = Keys.getLoadBalancerKey(id, account, region)
    Collection<CacheData> lbCache = cacheView.getAll(LOAD_BALANCERS.ns, lbKey)

    def lbDetails = lbCache.collect {
      def lbDetail = new TencentLoadBalancerDetail()
      lbDetail.id = it.attributes.id
      lbDetail.name = it.attributes.name
      lbDetail.account = account
      lbDetail.region = region
      lbDetail.vpcId = it.attributes.vpcId
      lbDetail.subnetId = it.attributes.subnetId
      lbDetail.loadBalancerType = it.attributes.loadBalancerType
      lbDetail.createTime = it.attributes.createTime
      lbDetail.loadBalacnerVips = it.attributes.loadBalacnerVips
      lbDetail.securityGroups = it.attributes.securityGroups
      lbDetail.listeners = it.attributes.listeners
      lbDetail
    }
    return lbDetails
  }

  @Override
  List<LoadBalancerProvider.Item> list() {
    log.info("Enter list loadBalancer")
    def searchKey = Keys.getLoadBalancerKey('*', '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    getSummaryForLoadBalancers(identifiers).values() as List
  }

  @Override
  LoadBalancerProvider.Item get(String id) {
    log.info("Enter Get loadBalancer id ${id}")
    def searchKey = Keys.getLoadBalancerKey(id, '*', '*')
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey).findAll {
      def key = Keys.parse(it)
      key.id == id
    }
    getSummaryForLoadBalancers(identifiers).get(id)
  }


  private Map<String, TencentLoadBalancerSummary> getSummaryForLoadBalancers(Collection<String> loadBalancerKeys) {
    Map<String, TencentLoadBalancerSummary> map = [:]
    Collection<CacheData> loadBalancerData = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys)
    Map<String, CacheData> loadBalancers = loadBalancerData.collectEntries { [(it.id): it] }

    for (lb in loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers[lb]
      if (loadBalancerFromCache) {
        def parts = Keys.parse(lb)
        String name = parts.id    //loadBalancerId
        String region = parts.region
        String account = parts.account
        def summary = map.get(name)
        if (!summary) {
          summary = new TencentLoadBalancerSummary(name: name)
          map.put name, summary
        }
        def loadBalancer = new TencentLoadBalancerDetail()
        loadBalancer.account = parts.account
        loadBalancer.region = parts.region
        loadBalancer.id = parts.id
        loadBalancer.vpcId = loadBalancerFromCache.attributes.vpcId
        loadBalancer.name = loadBalancerFromCache.attributes.name

        summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
      }
    }
    return map
  }


  private Set<TencentLoadBalancer> translateLoadBalancersFromCacheData(Collection<CacheData> loadBalancerData) {

    def transformed = loadBalancerData.collect(this.&fromCacheData)
    return transformed

/*
    Set<TencentLoadBalancer> loadBalancers = loadBalancerData.collect {
      def loadBalancer = new TencentLoadBalancer()
      loadBalancer.accountName = it.attributes.accountName
      loadBalancer.name = it.attributes.name
      loadBalancer.region = it.attributes.region
      loadBalancer.id = it.attributes.id
      loadBalancer.application = it.attributes.application
      loadBalancer.loadBalancerId = it.attributes.loadBalancerId
      loadBalancer.loadBalancerName = it.attributes.loadBalancerName
      loadBalancer.vpcId = it.attributes.vpcId
      //listeners
      loadBalancer.listeners = it.attributes.listeners.collect { listenerEntry ->
        def listener = new TencentLoadBalancerListener()
        listener.listenerId = listenerEntry.listenerId
        listener.listenerName = listenerEntry.listenerName
        listener.protocol = listenerEntry.protocol
        listener.port = listenerEntry.port
        listener.sessionExpireTime = listenerEntry.sessionExpireTime
        listener.scheduler = listenerEntry.scheduler
        listener.sniSwitch = listenerEntry.sniSwitch
        //rules
        listener.rules = listenerEntry.rules.collect { ruleEntry ->
          def rule = new TencentLoadBalancerRule()
          rule.locationId = ruleEntry.locationId
          rule.domain = ruleEntry.domain
          rule.url = ruleEntry.url
          rule.scheduler = ruleEntry.scheduler
          rule.sessionExpireTime = ruleEntry.sessionExpireTime
          //ruleTargets
          rule.targets = ruleEntry.targets.collect { ruleTargetEntry ->
            def ruleTarget = new TencentLoadBalancerTarget()
            ruleTarget.instanceId = ruleTargetEntry.instanceId
            ruleTarget.port = ruleTargetEntry.port
            ruleTarget.weight = ruleTargetEntry.weight
            ruleTarget.type = ruleTargetEntry.type
            ruleTarget
          }
          rule
        }
        //targets
        listener.targets = listenerEntry.targets.collect { targetEntry ->
          def target = new TencentLoadBalancerTarget()
          target.instanceId = targetEntry.instanceId
          target.port = targetEntry.port
          target.weight = targetEntry.weight
          target.type = targetEntry.type
          target
        }
        listener
      } //end listener

      loadBalancer
    }
    return loadBalancers
    */
  }

  // view models...

  static class TencentLoadBalancerSummary implements LoadBalancerProvider.Item {
    private Map<String, TencentLoadBalancerAccount> mappedAccounts = [:]
    String name

    TencentLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new TencentLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<TencentLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class TencentLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private Map<String, TencentLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    TencentLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new TencentLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<TencentLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class TencentLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {
    String name
    List<TencentLoadBalancerSummary> loadBalancers
  }

  static class TencentLoadBalancerDetail implements LoadBalancerProvider.Details {
    String account
    String region
    String name
    String id      //locadBalancerId
    String type = TencentCloudProvider.ID
    String loadBalancerType     //OPEN:公网, INTERNAL:内网
    Integer forwardType         //1:应用型,0:传统型
    String vpcId
    String subnetId
    Integer projectId
    String createTime
    List<String> loadBalacnerVips
    List<String> securityGroups
    List<TencentLoadBalancerListener> listeners
  }

}
