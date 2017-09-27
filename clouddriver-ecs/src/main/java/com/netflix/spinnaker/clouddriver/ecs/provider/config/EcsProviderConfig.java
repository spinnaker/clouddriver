package com.netflix.spinnaker.clouddriver.ecs.provider.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials.AWSRegion;

@Configuration
@EnableConfigurationProperties(ReservationReportConfigurationProperties.class)
public class EcsProviderConfig {
  @Bean
  @DependsOn("netflixECSCredentials")
  public EcsProvider ecsProvider(AccountCredentialsRepository accountCredentialsRepository, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    EcsProvider provider = new EcsProvider(accountCredentialsRepository, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));
    synchronizeEcsProvider(provider, accountCredentialsRepository, amazonClientProvider, awsCredentialsProvider);
    return provider;
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public EcsProviderSynchronizer synchronizeEcsProvider(EcsProvider ecsProvider, AccountCredentialsRepository accountCredentialsRepository,
                                                        AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {

    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(ecsProvider);
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials.class);
    List<Agent> newAgents = new LinkedList<>();

    for (NetflixAmazonCredentials credentials : allAccounts) {
      if(credentials.getCloudProvider().equals("ecs")) {
        for (AWSRegion region : credentials.getRegions()) {
          if (!scheduledAccounts.contains(credentials.getName())) {
            //newAgents.add(new ServiceCachingAgent(credentials.getName(), region.getName(), amazonClientProvider, awsCredentialsProvider));
          }
        }
      }
    }

    ecsProvider.getAgents().addAll(newAgents);
    return new EcsProviderSynchronizer();
  }

  class EcsProviderSynchronizer {
  }
}
