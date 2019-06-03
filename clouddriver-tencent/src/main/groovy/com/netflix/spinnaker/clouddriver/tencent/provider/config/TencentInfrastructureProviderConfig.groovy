package com.netflix.spinnaker.clouddriver.tencent.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentImageCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentInstanceTypeCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentKeyPairCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentLoadBalancerInstanceStateCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentNetworkCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentSecurityGroupCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.TencentSubnetCachingAgent
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import com.netflix.spinnaker.config.TencentConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Import

@Configuration
@Import(TencentConfiguration)
@EnableConfigurationProperties
class TencentInfrastructureProviderConfig {
  @Autowired
  Registry registry

  @Bean
  @DependsOn('tencentNamedAccountCredentials')
  TencentInfrastructureProvider tencentInfrastructureProvider(
    AccountCredentialsRepository accountCredentialsRepository,
    ObjectMapper objectMapper,
    Registry registry) {

    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof TencentNamedAccountCredentials
    } as Collection<TencentNamedAccountCredentials>

    // enable multiple accounts and multiple regions in each account
    allAccounts.each { TencentNamedAccountCredentials credential ->
      credential.regions.each { region ->
        agents << new TencentServerGroupCachingAgent(
          credential,
          objectMapper,
          registry,
          region.name)

        agents << new TencentInstanceTypeCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentKeyPairCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentImageCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentInstanceCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentLoadBalancerCachingAgent(
          credential,
          objectMapper,
          registry,
          region.name
        )

        agents << new TencentSecurityGroupCachingAgent(
          credential,
          objectMapper,
          registry,
          region.name
        )

        agents << new TencentNetworkCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentSubnetCachingAgent(
          credential,
          objectMapper,
          region.name
        )

        agents << new TencentLoadBalancerInstanceStateCachingAgent(
          credential,
          objectMapper,
          region.name
        )
      }
    }
    return new TencentInfrastructureProvider(agents)
  }
}
