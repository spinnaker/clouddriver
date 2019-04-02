package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

public class CloudFoundryCredentialsSynchronizer implements CredentialsInitializerSynchronizable {

  private CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties;
  private CatsModule catsModule;
  private AccountCredentialsRepository accountCredentialsRepository;

  public CloudFoundryCredentialsSynchronizer(CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
                                             AccountCredentialsRepository accountCredentialsRepository,
                                             CatsModule catsModule) {
    this.cloudFoundryConfigurationProperties = cloudFoundryConfigurationProperties;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.catsModule = catsModule;
  }

  /**
   * Only for backwards compatibility
   */
  @Override
  public String getCredentialsSynchronizationBeanName() {
    return "cloudFoundryCredentialsInitializer";
  }

  @Override
  @PostConstruct
  public List<? extends CloudFoundryCredentials> synchronize() {
    List<?> deltas = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, CloudFoundryCredentials.class,
      cloudFoundryConfigurationProperties.getAccounts());

    List<CloudFoundryConfigurationProperties.ManagedAccount> accountsToAdd = (List<CloudFoundryConfigurationProperties.ManagedAccount>) deltas.get(0);
    List<String> namesOfDeletedAccounts = (List<String>) deltas.get(1);

    for (CloudFoundryConfigurationProperties.ManagedAccount managedAccount : accountsToAdd) {
      CloudFoundryCredentials cloudFoundryAccountCredentials = new CloudFoundryCredentials(
        managedAccount.getName(),
        managedAccount.getAppsManagerUri(),
        managedAccount.getMetricsUri(),
        managedAccount.getApi(),
        managedAccount.getUser(),
        managedAccount.getPassword(),
        managedAccount.getEnvironment()
      );
      accountCredentialsRepository.save(managedAccount.getName(), cloudFoundryAccountCredentials);
    }

    // Possible NPE on catsModule?
    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule);

    return accountCredentialsRepository.getAll().stream()
      .filter(CloudFoundryCredentials.class::isInstance)
      .map(CloudFoundryCredentials.class::cast)
      .collect(Collectors.toList());
  }
}
