package com.netflix.spinnaker.clouddriver.dcos.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosSecretsCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.fiat.model.resources.Account
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Will Gorman
 */
@Configuration
class DcosProviderConfig {
  @Bean
  @DependsOn('dcosCredentials')
  DcosProvider dcosProvider(DcosCloudProvider dcosCloudProvider,
                            AccountCredentialsProvider accountCredentialsProvider,
                            AccountCredentialsRepository accountCredentialsRepository,
                            ObjectMapper objectMapper,
                            Registry registry) {

    def provider = new DcosProvider(dcosCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))
    synchronizeDcosProvider(provider, accountCredentialsProvider, accountCredentialsRepository, objectMapper, registry)
    provider
  }

  @Bean
  DcosProviderSynchronizerTypeWrapper dcosProviderSynchronizerTypeWrapper() {
    new DcosProviderSynchronizerTypeWrapper()
  }

  // TODO this doesnt appear to be getting used, since we're not running a scheduled synchronizer
  // i'll be honest i don't know what this does but all the providers have one so...
  class DcosProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {

    @Override
    Class getSynchronizerType() {
      return DcosProviderSynchronizer
    }
  }

  class DcosProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  DcosProviderSynchronizer synchronizeDcosProvider(DcosProvider dcosProvider,
                                                   AccountCredentialsProvider accountCredentialsProvider,
                                                   AccountCredentialsRepository accountCredentialsRepository,
                                                   ObjectMapper objectMapper,
                                                   Registry registry) {

    def accounts = ProviderUtils.getScheduledAccounts(dcosProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, DcosCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)


    def newlyAddedAgents = []
    allAccounts.each { DcosCredentials credentials ->
      if (!accounts.contains(credentials.name)) {

        newlyAddedAgents << new DcosSecretsCachingAgent(credentials.name,
          credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper)

        newlyAddedAgents << new DcosServerGroupCachingAgent(credentials.name,
          credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)

        newlyAddedAgents << new DcosLoadBalancerCachingAgent(credentials.name,
          credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)

        newlyAddedAgents << new DcosInstanceCachingAgent(credentials.name,
          credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper)
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      dcosProvider.agents.addAll(newlyAddedAgents)
    }

    new DcosProviderSynchronizer()
  }
}
