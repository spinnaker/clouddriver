package com.netflix.spinnaker.clouddriver.tencent.security

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.tencent.config.TencentConfigurationProperties
import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class TencentCredentialsInitializer implements CredentialsInitializerSynchronizable {
  @Bean
  List<TencentNamedAccountCredentials> tencentNamedAccountCredentials(
    TencentConfigurationProperties tencentConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository,
    String clouddriverUserAgentApplicationName
  ) {
    def tencentAccounts = []
    tencentConfigurationProperties.accounts.each {
      TencentConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def tencentAccount = new TencentNamedAccountCredentials(
          managedAccount.name,
          managedAccount.environment ?: managedAccount.name,
          managedAccount.accountType ?: managedAccount.name,
          managedAccount.secretId,
          managedAccount.secretKey,
          managedAccount.regions,
          clouddriverUserAgentApplicationName
        )
        tencentAccounts << (accountCredentialsRepository.save(managedAccount.name, tencentAccount)
        as TencentNamedAccountCredentials)
      } catch (e) {
        log.error("Could not load account ${managedAccount.name} for Tencent.", e)
      }
    }
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeTencentAccounts"
  }
}
