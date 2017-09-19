package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
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
  public List<? extends NetflixAmazonCredentials> netflixECSCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                        ECSCredentialsConfig credentialsConfig,
                                                                        AccountCredentialsRepository accountCredentialsRepository) throws Throwable {
    return synchronizeECSAccounts(credentialsLoader, credentialsConfig, accountCredentialsRepository);
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> synchronizeECSAccounts(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                         ECSCredentialsConfig ecsCredentialsConfig,
                                                                         AccountCredentialsRepository accountCredentialsRepository) throws Throwable {

    // TODO: add support for mutable accounts.
    //List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAmazonCredentials.class, accounts);
    List<NetflixAmazonCredentials> credentials = new LinkedList<>();

    for (AccountCredentials accountCredentials : accountCredentialsRepository.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials) {
        for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
          if (ecsAccount.getAwsAccount().equals(accountCredentials.getName())) {

            // TODO: accountCredentials should be serializable or somehow cloneable.
            CredentialsConfig.Account account = new CredentialsConfig.Account();
            account.setName(ecsAccount.getName());
            account.setAccountType("ecs");
            account.setAccountId(accountCredentials.getAccountId());
            account.setAllowPrivateThirdPartyImages(((NetflixAmazonCredentials) accountCredentials).getAllowPrivateThirdPartyImages());
            account.setBastionEnabled(((NetflixAmazonCredentials) accountCredentials).getBastionEnabled());
            account.setBastionHost(((NetflixAmazonCredentials) accountCredentials).getBastionHost());
            account.setEdda(account.getEdda());
            accountCredentials.getCredentials();
            account.setAssumeRole(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getAssumeRole());
            account.setDiscoveryEnabled(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getDiscoveryEnabled());
            account.setDiscovery(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getDiscovery());
            account.setDefaultKeyPair(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getDefaultKeyPair());
            account.setDefaultSecurityGroups(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getDefaultSecurityGroups());
            account.setEddaEnabled(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getEddaEnabled());
            account.setEnvironment(accountCredentials.getEnvironment());
            account.setFront50(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getFront50());
            account.setFront50Enabled(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getFront50Enabled());
            account.setRequiredGroupMembership(accountCredentials.getRequiredGroupMembership());
            account.setSessionName(((NetflixAssumeRoleAmazonCredentials) accountCredentials).getSessionName());

            List<CredentialsConfig.Region> regions = new LinkedList<>();
            for (AmazonCredentials.AWSRegion awsRegion : ((NetflixAssumeRoleAmazonCredentials) accountCredentials).getRegions()) {
              CredentialsConfig.Region region = new CredentialsConfig.Region();
              region.setAvailabilityZones((List<String>) awsRegion.getAvailabilityZones());
              region.setDeprecated(awsRegion.getDeprecated());
              region.setName(awsRegion.getName());
              region.setPreferredZones((List<String>) awsRegion.getPreferredZones());
            }
            account.setRegions(regions);

            List<CredentialsConfig.LifecycleHook> hooks = new LinkedList<>();
            for (AmazonCredentials.LifecycleHook awsHook : ((NetflixAssumeRoleAmazonCredentials) accountCredentials).getLifecycleHooks()) {
              CredentialsConfig.LifecycleHook hook = new CredentialsConfig.LifecycleHook();
              hook.setDefaultResult(awsHook.getDefaultResult());
              hook.setHeartbeatTimeout(awsHook.getHeartbeatTimeout());
              hook.setLifecycleTransition(awsHook.getLifecycleTransition());
              hook.setNotificationTargetARN(awsHook.getNotificationTargetARN());
              hook.setRoleARN(awsHook.getRoleARN());
            }
            account.setLifecycleHooks(hooks);

            Permissions permissions = ((NetflixAssumeRoleAmazonCredentials) accountCredentials).getPermissions();
            Permissions.Builder permBuilder = new Permissions.Builder();
            for (String group : permissions.allGroups()) {
              List<String> roles = new LinkedList<>();
              roles.add(group);
              for (Authorization auth : permissions.getAuthorizations(roles)) {
                permBuilder.add(auth, group);
              }
            }
            account.setPermissions(permBuilder);

            List<CredentialsConfig.Account> accounts = new LinkedList<>();
            accounts.add(account);

            CredentialsConfig ecsCopy = new CredentialsConfig();
            ecsCopy.setAccounts(accounts);

            NetflixAmazonCredentials clone = credentialsLoader.load(ecsCopy).get(0);
            clone.CLOUD_PROVIDER = "ecs";
            credentials.add(clone);

            accountCredentialsRepository.save(ecsAccount.getName(), clone);
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
