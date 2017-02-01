package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.PollingDcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.health.DcosHealthIndicator
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.beans.factory.annotation.Qualifier
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
  DcosConfigurationProperties dcosConfigurationProperties() {
    new DcosConfigurationProperties()
  }

  @Bean
  List<DcosCredentials> dcosCredentials(DcosConfigurationProperties dcosConfigurationProperties,
                                        AccountCredentialsRepository repository) {
    List<DcosCredentials> accounts = new ArrayList<>()
    for (DcosConfigurationProperties.Account account in dcosConfigurationProperties.accounts) {
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

  @Bean
  OperationPoller dcosOperationPoller(DcosConfigurationProperties properties) {
    new OperationPoller(
            properties.asyncOperationTimeoutSecondsDefault,
            properties.asyncOperationMaxPollingIntervalSeconds
    )
  }

  @Bean
  DcosDeploymentMonitor dcosDeploymentMonitor(@Qualifier("dcosOperationPoller") OperationPoller operationPoller) {
    new PollingDcosDeploymentMonitor(operationPoller)
  }
}


