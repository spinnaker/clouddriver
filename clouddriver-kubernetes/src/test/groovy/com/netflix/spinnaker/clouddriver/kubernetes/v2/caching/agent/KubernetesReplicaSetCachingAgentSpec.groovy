/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1beta1ReplicaSet
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesReplicaSetCachingAgentSpec extends Specification {
  def ACCOUNT = "my-account"
  def CLUSTER = "my-cluster"
  def APPLICATION = "my-application"
  def NAME = "the-name"
  def NAMESPACE = "your-namespace"

  void "invokes caching agent on output replica set"() {
    setup:
    def replicaSet = new V1beta1ReplicaSet()
    def annotations = [
        'relationships.spinnaker.io/cluster': '"' + CLUSTER + '"',
        'relationships.spinnaker.io/application': '"' + APPLICATION + '"'
    ]

    def metadata = new V1ObjectMeta()
    metadata.setAnnotations(annotations)
    metadata.setName(NAME)
    metadata.setNamespace(NAMESPACE)
    replicaSet.setMetadata(metadata)
    replicaSet.setKind(KubernetesKind.REPLICA_SET.name)
    replicaSet.setApiVersion(KubernetesApiVersion.EXTENSIONS_V1BETA1.name)

    def credentials = Mock(KubernetesV2Credentials)
    credentials.getDeclaredNamespaces() >> [NAMESPACE]
    credentials.listAllReplicaSets(NAMESPACE) >> [replicaSet]

    def namedAccountCredentials = Mock(KubernetesNamedAccountCredentials)
    namedAccountCredentials.getCredentials() >> credentials
    namedAccountCredentials.getName() >> ACCOUNT

    def registryMock = Mock(Registry)
    registryMock.timer(_) >> null
    def cachingAgent = new KubernetesReplicaSetCachingAgent(namedAccountCredentials, new ObjectMapper(), registryMock, 0, 1)
    def providerCacheMock = Mock(ProviderCache)
    providerCacheMock.getAll(_, _) >> []

    when:
    def result = cachingAgent.loadData(providerCacheMock)

    then:
    result.cacheResults[KubernetesKind.REPLICA_SET.name].size() == 1
    def cacheData = result.cacheResults[KubernetesKind.REPLICA_SET.name].iterator().next()
    cacheData.relationships.get(Keys.LogicalKind.CLUSTER.toString()) == [Keys.cluster(ACCOUNT, CLUSTER)]
    cacheData.relationships.get(Keys.LogicalKind.APPLICATION.toString()) == [Keys.application(APPLICATION)]
    cacheData.attributes.get("name") == NAME
    cacheData.attributes.get("namespace") == NAMESPACE
  }

  @Unroll
  void "merges two cache data"() {
    when:
    def credentials = Mock(KubernetesV2Credentials)
    credentials.getDeclaredNamespaces() >> [NAMESPACE]

    def namedAccountCredentials = Mock(KubernetesNamedAccountCredentials)
    namedAccountCredentials.getCredentials() >> credentials
    namedAccountCredentials.getName() >> ACCOUNT

    def registryMock = Mock(Registry)
    registryMock.timer(_) >> null
    def cachingAgent = new KubernetesReplicaSetCachingAgent(namedAccountCredentials, new ObjectMapper(), registryMock, 0, 1)
    def a = new DefaultCacheData("id", attrA, relA)
    def b = new DefaultCacheData("id", attrB, relB)
    cachingAgent.mergeCacheData(a, b)

    then:
    b.getAttributes().collect { k, v -> a.getAttributes().get(k) == v }.every()
    b.getRelationships().collect { k, v -> v.collect { r -> b.getRelationships().get(k).contains(r) }.every() }.every()

    where:
    attrA      | attrB      | relA              | relB
    ["a": "b"] | ["c": "d"] | ["a": ["1", "2"]] | ["b": ["3", "4"]]
    [:]        | ["c": "d"] | [:]               | ["b": ["3", "4"]]
    [:]        | [:]        | [:]               | [:]
    ["a": "b"] | [:]        | ["a": ["1", "2"]] | [:]
  }
}
