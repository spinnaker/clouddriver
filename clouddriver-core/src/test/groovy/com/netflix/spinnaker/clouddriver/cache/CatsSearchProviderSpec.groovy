/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import spock.lang.Shared
import spock.lang.Specification;

class CatsSearchProviderSpec extends Specification {
  def catsInMemorySearchProperties = new CatsInMemorySearchProperties()
  def cache = Mock(ProviderCache)

  def instanceAgent = Stub(CachingAgent) {
    getProvidedDataTypes() >> [new AgentDataType("instances", AgentDataType.Authority.AUTHORITATIVE)]
  }

  def jobIdAgent = Stub(CachingAgent) {
    getProvidedDataTypes() >> [new AgentDataType("jobIds", AgentDataType.Authority.AUTHORITATIVE)]
  }

  def providers = [
    Stub(SearchableProvider) {
      supportsSearch('instances', _) >> true
      getAgents() >> [instanceAgent]
      parseKey(_) >> { String k -> return null }
    },
    Stub(SearchableProvider) {
      supportsSearch('instances', _) >> true
      getAgents() >> [instanceAgent]
      parseKey(_) >> { String k -> return ["originalKey": k] }
    },
    Stub(SearchableProvider) {
      supportsSearch('serverGroups', _) >> true
      getAgents() >> [jobIdAgent]
      parseKey(_) >> { String k -> return ["originalKey": k] }
    }
  ]

  def providerRegistry = Stub(ProviderRegistry) {
    getProviders() >> providers
    getProviderCache(_) >> cache
  }

  def catsSearchProvider = new CatsSearchProvider(catsInMemorySearchProperties, cache, providers, providerRegistry)

  @Shared
  def instanceIdentifiers = [
    "aws:instances:prod:us-west-2:I-1234",
    "aws:instances:prod:us-west-2:I-5678",
    "aws:instances:prod:us-west-2:I-9012",
    "aws:instances:prod:us-west-2:I-3456",
    "aws:instances:prod:us-west-2:I-7890",
  ]

  @Shared
  def serverGroupIdentifiers = [
    "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v001",
    "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v002",
    "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v005",
    "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v007"
  ]

  @Shared
  def jobIdIdentifiers = [
    "abc-xyz-001: titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v001",
    "abc-xyz-002: titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v002",
    "abc-xyz-003: titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v005",
    "abc-xyz-004: titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v007"
  ]

  def serverGroupsCache(String id) {

    Map jobIdsToServerGroupIdentifiers = [
      "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v001": "abc-xyz-001",
      "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v002": "abc-xyz-002",
      "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v005": "abc-xyz-003",
      "titus:serverGroups:V2:abc-test:titustestvpc:us-east-1:abc-test-v007": "abc-xyz-004"
    ]

    def cacheData = Mock(CacheData)
    cacheData.attributes >> ["job": ["id": jobIdsToServerGroupIdentifiers.get(id)]]
    return cacheData
  }

  def "should parse instance identifiers"() {
    given:
    cache.getIdentifiers("instances") >> { return instanceIdentifiers }
    cache.existingIdentifiers("instances", _ as Collection<String>) >> { t, i -> return i }

    when:
    catsSearchProvider.run()

    then:
    catsSearchProvider.cachedIdentifiersByType == [
      "instances": instanceIdentifiers.collect { it.toLowerCase() }
    ]
  }

  def "should handle unparseable instance identifiers"() {
    when:
    providers.clear()

    then:
    catsSearchProvider.run()

    then:
    catsSearchProvider.cachedIdentifiersByType == [:]

    when:
    providers.add(
      Mock(SearchableProvider) {
        parseKey(_) >> { String k -> return null }
      }
    )

    then:
    catsSearchProvider.cachedIdentifiersByType == [:]
  }

  def "should parse jobId identifiers"() {
    given:
    catsSearchProvider.titusEnabled = true
    cache.filterIdentifiers("serverGroups", "titus*") >> { return serverGroupIdentifiers }
    cache.getIdentifiers("instances") >> { return instanceIdentifiers }
    cache.get(_, _) >> { args -> return serverGroupsCache(args[1]) }
    cache.existingIdentifiers("jobIds", _ as Collection<String>) >> { t, i -> return i }

    when:
    catsSearchProvider.run()

    then:
    catsSearchProvider.cachedIdentifiersByType.get("jobIds").size() == jobIdIdentifiers.size()
  }
}
