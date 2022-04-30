/*
 * Copyright 2020 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent

import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys
import com.netflix.spinnaker.clouddriver.huaweicloud.client.HuaweiCloudClient
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class HuaweiCloudSecurityGroupCachingAgentSpec extends Specification {

  static final String REGION = 'cn-north-1'
  static final String ACCOUNT_NAME = 'some-account-name'

  void "should add security groups on initial run"() {
    setup:
      def registry = new DefaultRegistry()
      def cloudClient = Mock(HuaweiCloudClient);
      def credentials = Mock(HuaweiCloudNamedAccountCredentials)
      credentials.cloudClient >> cloudClient
      credentials.name >> ACCOUNT_NAME
      def ProviderCache providerCache = Mock(ProviderCache)

      @Subject
      HuaweiCloudSecurityGroupCachingAgent agent = new HuaweiCloudSecurityGroupCachingAgent(
          credentials, new ObjectMapper(), registry, REGION)

      def securityGroupA = SecurityGroup.builder()
         .name('name-a')
         .id('name-a')
         .build()

      def securityGroupB = SecurityGroup.builder()
         .name('name-b')
         .id('name-b')
         .build()

      def keyGroupA = Keys.getSecurityGroupKey(securityGroupA.name,
                                               securityGroupA.id,
                                               ACCOUNT_NAME,
                                               REGION)

      def keyGroupB = Keys.getSecurityGroupKey(securityGroupB.name,
                                               securityGroupB.id,
                                               ACCOUNT_NAME,
                                               REGION)

    when:
      def cache = agent.loadData(providerCache)

    then:
      1 * cloudClient.getSecurityGroups(REGION) >> [securityGroupA, securityGroupB]
      with(cache.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns)) { Collection<CacheData> cd ->
        cd.size() == 2
        cd.id.containsAll([keyGroupA, keyGroupB])
      }
      (0..1) * providerCache.getAll("onDemand", [keyGroupA, keyGroupB])
      (0..1) * providerCache.getAll("onDemand", [keyGroupB, keyGroupA])
  }
}
