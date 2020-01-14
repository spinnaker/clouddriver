package com.netflix.spinnaker.clouddriver.tencent.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.provider.agent.*;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration
@Import(com.netflix.spinnaker.config.TencentConfiguration.class)
@EnableConfigurationProperties
public class TencentInfrastructureProviderConfig {
  @Bean
  @DependsOn("tencentNamedAccountCredentials")
  public TencentInfrastructureProvider tencentInfrastructureProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      final ObjectMapper objectMapper,
      final Registry registry) {

    final List<CachingAgent> agents = new ArrayList<CachingAgent>();
    Collection<TencentNamedAccountCredentials> allAccounts =
        accountCredentialsRepository.getAll().stream()
            .filter(
                it -> {
                  return it instanceof TencentNamedAccountCredentials;
                })
            .map(it -> (TencentNamedAccountCredentials) it)
            .collect(Collectors.toList());

    // enable multiple accounts and multiple regions in each account
    allAccounts.forEach(
        credential -> {
          credential
              .getRegions()
              .forEach(
                  region -> {
                    agents.add(
                        new TencentServerGroupCachingAgent(
                            credential,
                            objectMapper,
                            registry,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentInstanceTypeCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentKeyPairCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentImageCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentInstanceCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentLoadBalancerCachingAgent(
                            credential,
                            objectMapper,
                            registry,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentSecurityGroupCachingAgent(
                            credential,
                            objectMapper,
                            registry,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentNetworkCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentSubnetCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));

                    agents.add(
                        new TencentLoadBalancerInstanceStateCachingAgent(
                            credential,
                            objectMapper,
                            ((TencentNamedAccountCredentials.TencentRegion) region).getName()));
                  });
        });
    return new TencentInfrastructureProvider(agents);
  }

  public Registry getRegistry() {
    return registry;
  }

  public void setRegistry(Registry registry) {
    this.registry = registry;
  }

  @Autowired private Registry registry;
}
