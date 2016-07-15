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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

class OpenstackLoadBalancerProviderSpec extends Specification {

  String account = 'test'
  String region = 'east'

  OpenstackLoadBalancerProvider loadBalancerProvider
  OpenstackClusterProvider clusterProvider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    cache = Mock(Cache)
    objectMapper = Mock(ObjectMapper)
    clusterProvider = Mock(OpenstackClusterProvider)
    loadBalancerProvider = new OpenstackLoadBalancerProvider(cache, objectMapper, clusterProvider)
  }

  void "test get all load balancers"() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    Map<String, Object> attributes = Mock(Map)
    String lbKey = Keys.getLoadBalancerKey("*", "*", "*")
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    List<String> filter = ['filter']
    List<String> sgKeys = ["${OpenstackCloudProvider.ID}:${SERVER_GROUPS.ns}:myapp-teststack:test:TTEOSCORE1:${name}"]

    when:
    Set<OpenstackLoadBalancer> result = loadBalancerProvider.getApplicationLoadBalancers('')

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> cacheDataList
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * cacheData.relationships >> [(SERVER_GROUPS.ns) : sgKeys]
    1 * clusterProvider.getServerGroup(account, region, name)
    result.size() == 1
    result[0] == loadBalancer
    noExceptionThrown()
  }

  void "test get all load balancers - throw exception"() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    Map<String, Object> attributes = Mock(Map)
    String lbKey = Keys.getLoadBalancerKey("*", "*", "*")
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    List<String> filter = ['filter']
    Throwable throwable = new JedisException('test')

    when:
    loadBalancerProvider.getApplicationLoadBalancers('')

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> { throw throwable }
    0 * cacheData.attributes
    0 * objectMapper.convertValue(attributes, OpenstackLoadBalancer)
    0 * cacheData.relationships
    0 * clusterProvider.getServerGroup(account, region, name)
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

  void 'test get load balancer by account, region, and name'() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    Map<String, Object> attributes = Mock(Map)
    String lbKey = Keys.getLoadBalancerKey(lbid, account, region)
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    List<String> filter = ['filter']
    List<String> sgKeys = ["${OpenstackCloudProvider.ID}:${SERVER_GROUPS.ns}:myapp-teststack:test:TTEOSCORE1:${name}"]

    when:
    Set<OpenstackLoadBalancer> result = loadBalancerProvider.getLoadBalancers(account, region, lbid)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> cacheDataList
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackLoadBalancer) >> loadBalancer
    1 * cacheData.relationships >> [(SERVER_GROUPS.ns) : sgKeys]
    1 * clusterProvider.getServerGroup(account, region, name)
    result.size() == 1
    result[0] == loadBalancer
    noExceptionThrown()
  }

  void "test get load balancer by account, region, and name- throw exception"() {
    given:
    String lbid = 'lb1'
    String name = 'myapp-teststack-v002'
    CacheData cacheData = Mock(CacheData)
    Collection<CacheData> cacheDataList = [cacheData]
    Map<String, Object> attributes = Mock(Map)
    String lbKey = Keys.getLoadBalancerKey(lbid, account, region)
    OpenstackLoadBalancer loadBalancer = Mock(OpenstackLoadBalancer) {
      it.id >> { lbid }
      it.account >> { account }
      it.region >> { region }
      it.serverGroups >> { [new LoadBalancerServerGroup(name: name)] }
    }
    List<String> filter = ['filter']
    Throwable throwable = new JedisException('test')

    when:
    loadBalancerProvider.getLoadBalancers(account, region, lbid)

    then:
    1 * cache.filterIdentifiers(LOAD_BALANCERS.ns, lbKey) >> filter
    1 * cache.getAll(LOAD_BALANCERS.ns, filter, _ as RelationshipCacheFilter) >> { throw throwable }
    0 * cacheData.attributes
    0 * objectMapper.convertValue(attributes, OpenstackLoadBalancer)
    0 * cacheData.relationships
    0 * clusterProvider.getServerGroup(account, region, name)
    Throwable thrownException = thrown(JedisException)
    throwable == thrownException
  }

}
