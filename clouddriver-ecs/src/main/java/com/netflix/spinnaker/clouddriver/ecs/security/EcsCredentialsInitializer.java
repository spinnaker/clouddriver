package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.LinkedList;
import java.util.List;

@Configuration
public class EcsCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("ecs")
  public ECSCredentialsConfig ecsCredentialsConfig() {
    return new ECSCredentialsConfig();
  }

  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> netflixECSCredentials(ECSCredentialsConfig credentialsConfig,
                                                                        AccountCredentialsRepository accountCredentialsRepository) throws Throwable {
    return synchronizeECSAccounts(credentialsConfig, accountCredentialsRepository);
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> synchronizeECSAccounts(ECSCredentialsConfig ecsCredentialsConfig,
                                                                         AccountCredentialsRepository accountCredentialsRepository) throws Throwable {

    // TODO: add support for mutable accounts.
    //List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAmazonCredentials.class, accounts);
    List<NetflixAmazonCredentials> credentials = new LinkedList<>();

    for (AccountCredentials accountCredentials : accountCredentialsRepository.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials) {
        for (int x = 0; x < ecsCredentialsConfig.getAccounts().size(); x++) {
          if (ecsCredentialsConfig.getAccounts().get(x).getAwsAccount().equals(accountCredentials.getName())) {
            NetflixAmazonCredentials netflixECSCredentials = (NetflixAmazonCredentials) accountCredentials;
            netflixECSCredentials.CLOUD_PROVIDER = "ecs";

            credentials.add(netflixECSCredentials);

            break;
          }
        }
      }
    }

    return credentials;
  }

  @Override
  public String getCredentialsSynchronizationBeanName() {
    return "synchronizeECSAccounts";
  }
}
