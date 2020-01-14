package com.netflix.spinnaker.clouddriver.tencent.security;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.tencent.config.TencentConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@Slf4j
public class TencentCredentialsInitializer implements CredentialsInitializerSynchronizable {
  @Bean
  public List<TencentNamedAccountCredentials> tencentNamedAccountCredentials(
      TencentConfigurationProperties tencentConfigurationProperties,
      final AccountCredentialsRepository accountCredentialsRepository,
      final String clouddriverUserAgentApplicationName) {
    final List<TencentNamedAccountCredentials> tencentAccounts = new ArrayList<>();
    tencentConfigurationProperties
        .getAccounts()
        .forEach(
            managedAccount -> {
              try {
                final String environment = managedAccount.getEnvironment();
                final String type = managedAccount.getAccountType();
                TencentNamedAccountCredentials tencentAccount =
                    new TencentNamedAccountCredentials(
                        managedAccount.getName(),
                        !StringUtils.isEmpty(environment) ? environment : managedAccount.getName(),
                        !StringUtils.isEmpty(type) ? type : managedAccount.getName(),
                        managedAccount.getSecretId(),
                        managedAccount.getSecretKey(),
                        managedAccount.getRegions(),
                        clouddriverUserAgentApplicationName);
                tencentAccounts.add(
                    (TencentNamedAccountCredentials)
                        accountCredentialsRepository.save(
                            managedAccount.getName(), tencentAccount));
              } catch (Exception e) {
                log.error(
                    "Could not load account " + managedAccount.getName() + " for Tencent.", e);
              }
            });
    return tencentAccounts;
  }

  public String getCredentialsSynchronizationBeanName() {
    return "synchronizeTencentAccounts";
  }
}
