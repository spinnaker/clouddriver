package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.dcos.health.DcosHealthIndicator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty('dcos.enabled')
@EnableConfigurationProperties
@ComponentScan(["com.netflix.spinnaker.clouddriver.dcos"])
class DcosConfiguration {
  @Bean
  @ConfigurationProperties("dcos")
  DcosCredentialsConfig dcosCredentialsConfig() {
    new DcosCredentialsConfig()
  }

  @Bean
  List<DcosCredentials> dcosCredentials(DcosCredentialsConfig dcosCredentialsConfig,
                                        AccountCredentialsRepository repository) {
    List<DcosCredentials> accounts = new ArrayList<>()
    for (DcosCredentialsConfig.Account account in dcosCredentialsConfig.accounts) {
      DcosCredentials credentials = new DcosCredentials(account.name, account.environment, account.accountType, account.dcosUrl, account.user, account.password)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  DcosClientProvider dcosClientProvider(Registry registry) {
    new DcosClientProvider(registry)
  }

  @Bean
  DcosHealthIndicator dcosHealthIndicator(AccountCredentialsProvider accountCredentialsProvider, DcosClientProvider dcosClientProvider) {
    new DcosHealthIndicator(accountCredentialsProvider, dcosClientProvider)
  }

  @Bean
  DeployDcosServerGroupDescriptionToAppMapper deployDcosServerGroupDescriptionToAppMapper() {
    new DeployDcosServerGroupDescriptionToAppMapper()
  }

  static class DcosCredentialsConfig {
    List<Account> accounts = []

    static class Account {
      String name
      String environment
      String accountType
      String dcosUrl
      String user
      String password
    }
  }
}


