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

package com.netflix.spinnaker.clouddriver.aws.provider.config

import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonElasticIpCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonInstanceTypeCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonKeyPairCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonSecurityGroupCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonSubnetCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonVpcCachingAgent
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@Configuration
class AwsInfrastructureProviderConfig {
  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsInfrastructureProvider awsInfrastructureProvider(AmazonCloudProvider amazonCloudProvider,
                                                      AmazonClientProvider amazonClientProvider,
                                                      AccountCredentialsRepository accountCredentialsRepository,
                                                      AmazonObjectMapper amazonObjectMapper,
                                                      Registry registry) {
    def awsInfrastructureProvider =
      new AwsInfrastructureProvider(amazonCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsInfrastructureProvider(awsInfrastructureProvider,
                                         amazonCloudProvider,
                                         amazonClientProvider,
                                         accountCredentialsRepository,
                                         amazonObjectMapper,
                                         registry)

    awsInfrastructureProvider
  }

  @Bean
  AwsInfrastructureProviderSynchronizerTypeWrapper awsInfrastructureProviderSynchronizerTypeWrapper() {
    new AwsInfrastructureProviderSynchronizerTypeWrapper()
  }

  class AwsInfrastructureProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return AwsInfrastructureProviderSynchronizer
    }
  }

  class AwsInfrastructureProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  AwsInfrastructureProviderSynchronizer synchronizeAwsInfrastructureProvider(AwsInfrastructureProvider awsInfrastructureProvider,
                                                                             AmazonCloudProvider amazonCloudProvider,
                                                                             AmazonClientProvider amazonClientProvider,
                                                                             AccountCredentialsRepository accountCredentialsRepository,
                                                                             AmazonObjectMapper amazonObjectMapper,
                                                                             Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    allAccounts.each { NetflixAmazonCredentials credentials ->
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        if (!scheduledAccounts.contains(credentials.name)) {
          def newlyAddedAgents = []

          newlyAddedAgents << new AmazonElasticIpCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name)
          newlyAddedAgents << new AmazonInstanceTypeCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name)
          newlyAddedAgents << new AmazonKeyPairCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name)
          newlyAddedAgents << new AmazonSecurityGroupCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, amazonObjectMapper, registry)
          newlyAddedAgents << new AmazonSubnetCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, amazonObjectMapper)
          newlyAddedAgents << new AmazonVpcCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, amazonObjectMapper)

          // If there is an agent scheduler, then this provider has been through the AgentController in the past.
          // In that case, we need to do the scheduling here (because accounts have been added to a running system).
          if (awsInfrastructureProvider.agentScheduler) {
            ProviderUtils.rescheduleAgents(awsInfrastructureProvider, newlyAddedAgents)
          }

          awsInfrastructureProvider.agents.addAll(newlyAddedAgents)
        }
      }
    }

    new AwsInfrastructureProviderSynchronizer()
  }
}
