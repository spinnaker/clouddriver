/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusClusterCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import javax.inject.Provider

@Configuration
class TitusCachingProviderConfig {

  @Value('${titus.pollIntervalMillis:30000}')
  Long pollIntervalMillis

  @Value('${titus.timeoutMillis:300000}')
  Long timeOutMilis

  @Bean
  @DependsOn('netflixTitusCredentials')
  TitusCachingProvider titusCachingProvider(AccountCredentialsRepository accountCredentialsRepository,
                                            TitusCloudProvider titusCloudProvider,
                                            TitusClientProvider titusClientProvider,
                                            ObjectMapper objectMapper,
                                            Registry registry,
                                            Provider<AwsLookupUtil> awsLookupUtilProvider) {
    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof NetflixTitusCredentials
    } as Collection<NetflixTitusCredentials>
    allAccounts.each { NetflixTitusCredentials account ->
      account.regions.each { region ->
        agents << new TitusClusterCachingAgent(
          titusCloudProvider,
          titusClientProvider,
          account,
          region,
          objectMapper,
          registry,
          awsLookupUtilProvider,
          pollIntervalMillis,
          timeOutMilis
        )
      }
    }
    new TitusCachingProvider(agents)
  }

  @Bean
  ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper()
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper
  }
}
