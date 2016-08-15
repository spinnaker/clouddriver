/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPool
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

@Unroll
class OpenstackServerGroupCachingAgentSpec extends Specification {

  OpenstackServerGroupCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackClientProvider provider
  @Shared
  String region = 'east'
  @Shared
  String account = 'test'
  ObjectMapper objectMapper
  Registry registry

  void "setup"() {
    namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    provider = Mock(OpenstackClientProvider)
    objectMapper = new ObjectMapper()
    registry = Stub(Registry) {
      timer(_, _) >> Mock(Timer)
    }
    cachingAgent = Spy(OpenstackServerGroupCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper, registry]) {
      getAccountName() >> account
      getClientProvider() >> provider
    }
  }

  void "test load data"() {
    given:
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)

    and:
    ProviderCache providerCache = Mock(ProviderCache)
    Stack stack = Mock(Stack) {
      getName() >> serverGroupName
    }
    List<Stack> stacks = [stack]

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.listStacks(region) >> stacks
    1 * providerCache.getAll(ON_DEMAND.ns, [serverGroupKey]) >> []
    1 * cachingAgent.buildCacheResult(providerCache, _, stacks) >> { cache, builder, stackz -> builder.build() }

    and:
    result.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions.isEmpty()
  }

  void "test load data demand to keep"() {
    given:
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    String cacheId = UUID.randomUUID().toString()

    and:
    ProviderCache providerCache = Mock(ProviderCache)
    Stack stack = Mock(Stack) {
      getName() >> serverGroupName
    }
    CacheData cacheData = new DefaultCacheData(cacheId, ['cacheTime': System.currentTimeMillis()], [:])
    List<Stack> stacks = [stack]

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.listStacks(region) >> stacks
    1 * providerCache.getAll(ON_DEMAND.ns, [serverGroupKey]) >> [cacheData]
    1 * cachingAgent.buildCacheResult(providerCache, _, stacks) >> { cache, builder, stackz -> builder.build() }

    and:
    result.cacheResults[ON_DEMAND.ns].first() == cacheData
    result.evictions.isEmpty()
  }

  void "test load data evict data"() {
    given:
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    String cacheId = UUID.randomUUID().toString()

    and:
    ProviderCache providerCache = Mock(ProviderCache)
    Stack stack = Mock(Stack) {
      getName() >> serverGroupName
    }
    CacheData cacheData = new DefaultCacheData(cacheId, ['cacheTime': 1, 'processedCount': 1], [:])
    List<Stack> stacks = [stack]

    when:
    CacheResult result = cachingAgent.loadData(providerCache)

    then:
    1 * provider.listStacks(region) >> stacks
    1 * providerCache.getAll(ON_DEMAND.ns, [serverGroupKey]) >> [cacheData]
    1 * cachingAgent.buildCacheResult(providerCache, _, stacks) >> { cache, builder, stackz -> builder.build() }

    and:
    result.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions[ON_DEMAND.ns] == [cacheId]
  }

  void "test load data exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * provider.listStacks(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  void "test build cache result"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String stackId = UUID.randomUUID().toString()
    String serverId = UUID.randomUUID().toString()
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String poolId = UUID.randomUUID().toString()
    String poolName = "$appName-lb"

    and:
    Server server = Mock(Server) {
      getId() >> { serverId }
    }
    Stack stack = Mock(Stack) {
      getId() >> { stackId }
      getName() >> { serverGroupName }
    }
    LbPool pool = Mock(LbPool) {
      getId() >> { poolId }
      getName() >> { poolName }
    }
    Stack stackDetail = Mock(Stack) { getParameters() >> ['pool_id': poolId] }
    OpenstackServerGroup openstackServerGroup = OpenstackServerGroup.builder().account(account).name(serverGroupName).build()
    Map<String, Object> serverGroupAttributes = objectMapper.convertValue(openstackServerGroup, OpenstackInfrastructureProvider.ATTRIBUTES)
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    and:
    String clusterKey = Keys.getClusterKey(account, appName, clusterName)
    String appKey = Keys.getApplicationKey(appName)
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    String loadBalancerKey = Keys.getLoadBalancerKey(poolName, poolId, account, region)
    String instanceKey = Keys.getInstanceKey(serverId, account, region)

    when:
    cachingAgent.buildCacheResult(providerCache, cacheResultBuilder, [stack])

    then:
    1 * provider.getInstancesByServerGroup(region) >> [(stackId): [server]]
    1 * provider.getStack(region, stack.name) >> stackDetail
    1 * provider.getLoadBalancerPool(region, poolId) >> pool
    1 * cachingAgent.buildServerGroup(providerCache, stackDetail, _) >> openstackServerGroup

    and:
    CacheResult result = cacheResultBuilder.build()
    noExceptionThrown()

    and:
    Collection<CacheData> applicationData = result.cacheResults.get(APPLICATIONS.ns)
    applicationData.size() == 1
    applicationData.first().id == appKey
    applicationData.first().attributes == ['name': appName]
    applicationData.first().relationships == [(CLUSTERS.ns): Sets.newHashSet(clusterKey)]

    and:
    Collection<CacheData> clusterData = result.cacheResults.get(CLUSTERS.ns)
    clusterData.size() == 1
    clusterData.first().attributes == ['name': clusterName, 'accountName': account]
    clusterData.first().id == clusterKey
    clusterData.first().relationships == [(APPLICATIONS.ns): Sets.newHashSet(appKey), (SERVER_GROUPS.ns): Sets.newHashSet(serverGroupKey)]

    and:
    Collection<CacheData> serverGroupData = result.cacheResults.get(SERVER_GROUPS.ns)
    serverGroupData.size() == 1
    serverGroupData.first().id == serverGroupKey
    serverGroupData.first().attributes == serverGroupAttributes
    serverGroupData.first().relationships == [(APPLICATIONS.ns)    : Sets.newHashSet(appKey), (CLUSTERS.ns): Sets.newHashSet(clusterKey)
                                              , (LOAD_BALANCERS.ns): Sets.newHashSet(loadBalancerKey), (INSTANCES.ns): Sets.newHashSet(instanceKey)]
  }

  void "test build cache result exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    cachingAgent.buildCacheResult(providerCache, Mock(CacheResultBuilder), [Mock(Stack)])

    then:
    1 * provider.getInstancesByServerGroup(region) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  void "test build server group"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Stack stack = Mock(Stack)
    Set<String> loadBalancerIds = Sets.newHashSet('loadBalancerId')
    ZonedDateTime createdTime = ZonedDateTime.now()
    String subnetId = UUID.randomUUID().toString()

    and:
    Map<String, Object> launchConfig = Mock(Map)
    Map<String, Object> openstackImage = Mock(Map)
    Map<String, Object> scalingConfig = Mock(Map)
    Map<String, Object> buildInfo = Mock(Map)

    and:
    OpenstackServerGroup expected = OpenstackServerGroup.builder()
      .account(account)
      .region(region)
      .name(stack.name)
      .createdTime(createdTime.toInstant().toEpochMilli())
      .scalingConfig(scalingConfig)
      .launchConfig(launchConfig)
      .loadBalancers(loadBalancerIds)
      .image(openstackImage)
      .buildInfo(buildInfo)
      .disabled(loadBalancerIds.isEmpty())
      .subnetId(subnetId)
      .build()

    when:
    OpenstackServerGroup result = cachingAgent.buildServerGroup(providerCache, stack, loadBalancerIds)

    then:
    _ * stack.creationTime >> DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(createdTime.toInstant())
    _ * stack.parameters >> [subnet_id: subnetId]
    1 * cachingAgent.buildLaunchConfig(stack.parameters) >> launchConfig
    1 * cachingAgent.buildImage(providerCache, launchConfig.image) >> openstackImage
    1 * cachingAgent.buildScalingConfig(stack) >> scalingConfig
    1 * cachingAgent.buildInfo(openstackImage.properties) >> buildInfo

    and:
    expected == result
    noExceptionThrown()
  }

  void "test build launch config - #testCase"() {
    when:
    Map<String, Object> result = cachingAgent.buildLaunchConfig(parameters)

    then:
    result == expected
    noExceptionThrown()

    where:
    testCase | parameters                                                                                           | expected
    'empty'  | [:]                                                                                                  | [:]
    'normal' | [image: 'image', flavor: 'flavor', network_id: 'network', pool_id: 'portId', security_groups: 'a,b,c'] | [image: 'image', instanceType: 'flavor', networkId: 'network', loadBalancerId: 'portId', securityGroups: ['a', 'b', 'c']]
  }

  void "test build image config"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    String image = UUID.randomUUID().toString()
    String imagekey = Keys.getImageKey(image, account, region)

    when:
    Map<String, Object> result = cachingAgent.buildImage(providerCache, image)

    then:
    1 * providerCache.get(IMAGES.ns, imagekey) >> cacheData
    1 * cacheData.attributes >> attributes

    and:
    result == attributes
    noExceptionThrown()
  }

  void "test build image config - not found"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String image = UUID.randomUUID().toString()
    String imagekey = Keys.getImageKey(image, account, region)

    when:
    Map<String, Object> result = cachingAgent.buildImage(providerCache, image)

    then:
    1 * providerCache.get(IMAGES.ns, imagekey) >> null

    and:
    result == null
    noExceptionThrown()
  }

  void "test build image config - exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new JedisException('test')
    String image = UUID.randomUUID().toString()
    String imagekey = Keys.getImageKey(image, account, region)

    when:
    cachingAgent.buildImage(providerCache, image)

    then:
    1 * providerCache.get(IMAGES.ns, imagekey) >> { throw throwable }

    and:
    Throwable exception = thrown(JedisException)
    exception == throwable
  }

  void "test build scaling config - #testCase"() {
    when:
    Map<String, Object> result = cachingAgent.buildScalingConfig(stack).sort { it.key }

    then:
    result == expected
    noExceptionThrown()

    where:
    testCase  | stack               | expected
    'empty'   | null                | [:]
    'normal'  | buildStack(1, 5, 3) | [minSize: 1, maxSize: 5, desiredSize: 3, autoscalingType: 'cpu', scaleup:[cooldown: 60, period: 60, adjustment: 1, threshold: 50], scaledown:[cooldown: 60, period: 600, adjustment: -1, threshold: 15]].sort { it.key }
    'missing' | buildStack()        | [minSize: 0, maxSize: 0, desiredSize: 0, autoscalingType: 'cpu', scaleup: [cooldown:null, period:null, adjustment:null, threshold:null], scaledown: [cooldown:null, period:null, adjustment:null, threshold:null]].sort { it.key }
  }

  void "test build info config - #testCase"() {
    when:
    Map<String, Object> result = cachingAgent.buildInfo(properties)

    then:
    result == expected
    noExceptionThrown()

    where:
    testCase                          | properties                                                                                                  | expected
    'null'                            | null                                                                                                        | [:]
    'empty'                           | [:]                                                                                                         | [:]
    'appversion only'                 | ['appversion': 'helloworld-1.4.0-1140443.h420/build-huxtable/420']                                          | [packageName: 'helloworld', version: '1.4.0', commit: '1140443', jenkins: [name: 'build-huxtable', number: '420']]
    'appversion and host'             | [appversion: 'helloworld-1.4.0-1140443.h420/build-huxtable/420', build_host: 'host']                        | [packageName: 'helloworld', version: '1.4.0', commit: '1140443', jenkins: [name: 'build-huxtable', number: '420', host: 'host']]
    'appversion, host, and buildinfo' | [appversion: 'helloworld-1.4.0-1140443.h420/build-huxtable/420', build_host: 'host', build_info_url: 'url'] | [packageName: 'helloworld', version: '1.4.0', commit: '1140443', buildInfoUrl: 'url', jenkins: [name: 'build-huxtable', number: '420', host: 'host']]
  }

  void "test handle on demand no result - #testCase"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)

    when:
    OnDemandAgent.OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    result == null

    where:
    testCase                  | data
    'empty data'              | [:]
    'missing serverGroupName' | [account: account, region: region]
    'wrong account'           | [serverGroupName: 'name', account: 'abc', region: region]
    'wrong region'            | [serverGroupName: 'name', account: account, region: 'abc']
  }

  void "test handle on demand no stack"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    Map<String, Object> data = [serverGroupName: serverGroupName, account: account, region: region]

    when:
    OnDemandAgent.OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getStack(region, serverGroupName) >> null

    and:
    result.cacheResult.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions.get(SERVER_GROUPS.ns) == [serverGroupKey]
  }

  void "test handle on demand no cache results built"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    Map<String, Object> data = [serverGroupName: serverGroupName, account: account, region: region]
    Stack stack = Mock(Stack)

    when:
    OnDemandAgent.OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getStack(region, serverGroupName) >> stack
    1 * cachingAgent.buildCacheResult(providerCache, _, [stack]) >> { cache, builder, stackz -> builder.build() }
    1 * providerCache.evictDeletedItems(ON_DEMAND.ns, [serverGroupKey])

    and:
    result.cacheResult.cacheResults.get(ON_DEMAND.ns).isEmpty()
    result.evictions == [:]
  }

  void "test handle on demand store"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    Map<String, Object> data = [serverGroupName: serverGroupName, account: account, region: region]
    Stack stack = Mock(Stack)
    CacheData serverGroupCacheData = new DefaultCacheData(UUID.randomUUID().toString(), [:], [:])
    CacheResult cacheResult = new DefaultCacheResult([(SERVER_GROUPS.ns): [serverGroupCacheData]])

    when:
    OnDemandAgent.OnDemandResult result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getStack(region, serverGroupName) >> stack
    1 * cachingAgent.buildCacheResult(providerCache, _, [stack]) >> cacheResult
    1 * providerCache.putCacheData(ON_DEMAND.ns, _)

    and:
    result.cacheResult.cacheResults.get(SERVER_GROUPS.ns).first() == serverGroupCacheData
    result.evictions == [:]
  }

  void "test handles - #testCase"() {
    when:
    boolean result = cachingAgent.handles(type, cloudProvider)

    then:
    result == expected

    where:
    testCase         | type                                    | cloudProvider             | expected
    'wrong type'     | OnDemandAgent.OnDemandType.LoadBalancer | OpenstackCloudProvider.ID | false
    'wrong provider' | OnDemandAgent.OnDemandType.ServerGroup  | 'aws'                     | false
    'success'        | OnDemandAgent.OnDemandType.ServerGroup  | OpenstackCloudProvider.ID | true
  }

  void "test pending on demand requests"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    String appName = 'testapp'
    String clusterName = "${appName}-stack-detail"
    String serverGroupName = "${clusterName}-v000"
    String serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    Collection<String> keys = [serverGroupKey]
    CacheData cacheData = new DefaultCacheData(serverGroupKey, [cacheTime: System.currentTimeMillis(), processedCount: 1, processedTime: System.currentTimeMillis()], [:])

    when:
    Collection<Map> result = cachingAgent.pendingOnDemandRequests(providerCache)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> keys
    1 * providerCache.getAll(ON_DEMAND.ns, keys) >> [cacheData]

    and:
    result.first() == [details: Keys.parse(serverGroupKey), cacheTime: cacheData.attributes.cacheTime, processedCount: cacheData.attributes.processedCount, processedTime: cacheData.attributes.processedTime]
  }

  void "test pending on demand requests - exception"() {
    given:
    ProviderCache providerCache = Mock(ProviderCache)
    Throwable throwable = new JedisException('test')

    when:
    cachingAgent.pendingOnDemandRequests(providerCache)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> { throw throwable }

    and:
    Throwable exception = thrown(JedisException)
    exception == throwable
  }



  @Ignore
  protected Stack buildStack(Integer minSize = null, Integer maxSize = null, Integer desiredSize = null) {
    def params
    if (minSize && maxSize && desiredSize) {
      params = [
        min_size: minSize.toString(),
        max_size: maxSize.toString(),
        desired_size: desiredSize.toString(),
        autoscaling_type: 'cpu_util',
        scaleup_cooldown: '60',
        scaleup_adjustment: '1',
        scaleup_period: '60',
        scaleup_threshold: '50',
        scaledown_cooldown: '60',
        scaledown_adjustment: '-1',
        scaledown_period: '600',
        scaledown_threshold: '15'
      ]
    } else {
      params = [autoscaling_type: 'cpu_util']
    }
    Stub(Stack) {
      getParameters() >> { params }
    }
  }
}
