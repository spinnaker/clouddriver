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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent
import spock.lang.Specification
import spock.lang.Subject

class EcsClusterProviderSpec extends Specification {
  private static String ACCOUNT = 'test-account'
  private static String REGION = 'us-west-1'

  private Cache cacheView = Mock(Cache)
  @Subject
  private EcsClusterProvider ecsClusterProvider = new EcsClusterProvider(cacheView)

  def 'should get no clusters'() {
    given:
    cacheView.getAll(_) >> Collections.emptySet()

    when:
    def ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == 0
  }

  def 'should get a cluster'() {
    given:
    def clusterName = "test-cluster"
    def clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName
    def key = Keys.getClusterKey(ACCOUNT, REGION, clusterName)

    def keys = new HashSet()
    keys.add(key)

    def attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn)
    def cacheData = new HashSet()
    cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))

    cacheView.getAll(_) >> cacheData

    when:
    Collection<EcsCluster> ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == 1
    ecsClusters[0].getName() == clusterName
  }

  def 'should get multiple clusters'() {
    given:
    int numberOfClusters = 5
    Set<String> clusterNames = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Set<String> keys = new HashSet<>()

    for (int x = 0; x < numberOfClusters; x++) {
      String clusterName = "test-cluster-" + x
      String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName
      String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName)

      keys.add(key)
      clusterNames.add(clusterName)

      Map<String, Object> attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn)
      cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))
    }

    cacheView.getAll(_) >> cacheData

    when:
    Collection<EcsCluster> ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == numberOfClusters
    clusterNames.containsAll(ecsClusters*.getName())
    ecsClusters*.getName().containsAll(clusterNames)
  }
}
