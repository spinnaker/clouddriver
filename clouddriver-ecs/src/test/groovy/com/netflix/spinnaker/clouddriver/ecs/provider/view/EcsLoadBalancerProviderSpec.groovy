/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import spock.lang.Specification
import spock.lang.Subject

class EcsLoadBalancerProviderSpec extends Specification {
  def client = Mock(EcsLoadbalancerCacheClient)
  def ecsCredentialsConfig = Mock(ECSCredentialsConfig)
  @Subject
  def provider = new EcsLoadBalancerProvider(client, ecsCredentialsConfig)

  def 'should retrieve an empty list'() {
    when:
    def retrievedList = provider.list()

    then:
    client.findAll() >> Collections.emptyList()
    retrievedList.size() == 0
  }

  def 'should retrieve a list containing load balancers'() {
    given:
    def expectedNumberOfLoadbalancers = 2
    def givenList = []
    def accounts = []
    for (int x = 0; x < expectedNumberOfLoadbalancers; x++) {
      givenList << new EcsLoadBalancerCache(
        account: 'test-account-' + x,
        region: 'us-west-' + x,
        loadBalancerArn: 'arn',
        loadBalancerType: 'always-classic',
        cloudProvider: EcsCloudProvider.ID,
        listeners: [],
        scheme: 'scheme',
        availabilityZones: [],
        ipAddressType: 'ipv4',
        loadBalancerName: 'load-balancer-' + x,
        canonicalHostedZoneId: 'zone-id',
        vpcId: 'vpc-id-' + x,
        dnsname: 'dns-name',
        createdTime: System.currentTimeMillis(),
        subnets: [],
        securityGroups: [],
        targetGroups: ['target-group-' + x],
        serverGroups: []
      )

      accounts << new ECSCredentialsConfig.Account(
        name: 'test-account-' + x,
        awsAccount: 'test-account-' + x
      )
    }
    ecsCredentialsConfig.getAccounts() >> accounts

    when:
    def retrievedList = provider.list()

    then:
    client.findAll() >> givenList
    retrievedList.size() == expectedNumberOfLoadbalancers
    retrievedList*.getName().containsAll(givenList*.loadBalancerName)
  }
}
