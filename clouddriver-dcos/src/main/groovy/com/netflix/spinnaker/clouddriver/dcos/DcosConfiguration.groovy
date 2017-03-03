package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.PollingDcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.health.DcosHealthIndicator
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.model.DCOSAuthCredentials
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  private static final Logger LOGGER = LoggerFactory.getLogger(DcosConfiguration)

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
      Optional<DCOSAuthCredentials> dcosAuthCredentials = buildDCOSAuthCredentials(account.name, account.user, account.password, account.serviceId, account.serviceKey)

      if (dcosAuthCredentials.present) {
        DcosCredentials credentials = new DcosCredentials(account.name, account.environment, account.accountType, account.dcosUrl, dcosAuthCredentials.get())
        accounts.add(credentials)
        repository.save(account.name, credentials)
      }
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

  private static Optional<DCOSAuthCredentials> buildDCOSAuthCredentials(String accountName, String user, String password, String serviceId, String serviceKey) {
    Optional<DCOSAuthCredentials> dcosAuthCredentials = Optional.empty()

    if (user && password && (serviceId || serviceKey)) {
      LOGGER.warn("Ignoring account [${accountName}]: A user and password was given for the account, as was a serviceid and/or servicekey.")
    } else if (serviceId && serviceKey && (user || password)) {
      LOGGER.warn("Ignoring account [${accountName}]: A serviceid and servicekey was given for the account, as was a user and/or password.")
    } else if (user && password) {
      dcosAuthCredentials = Optional.of(DCOSAuthCredentials.forUserAccount(user, password))
    } else if (serviceId && serviceKey) {
      dcosAuthCredentials = Optional.of(DCOSAuthCredentials.forServiceAccount(serviceId, serviceKey))
    } else {
      LOGGER.warn("Ignoring account [${accountName}]: Neither a user/password or a serviceId/serviceKey combination was provided.")
    }

    return dcosAuthCredentials
  }
}


