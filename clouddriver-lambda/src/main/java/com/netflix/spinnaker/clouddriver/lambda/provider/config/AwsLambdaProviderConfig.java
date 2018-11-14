/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.lambda.provider.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.LambdaCloudProvider;
import com.netflix.spinnaker.clouddriver.lambda.provider.LambdaProvider;
import com.netflix.spinnaker.clouddriver.lambda.provider.agent.AmazonLambdaFunctionCachingAgent;
import com.netflix.spinnaker.clouddriver.lambda.provider.agent.IamPolicyReader;
import com.netflix.spinnaker.clouddriver.lambda.provider.agent.IamRoleCachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
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
public class AwsLambdaProviderConfig {

  @Bean
  public IamPolicyReader iamPolicyReader(ObjectMapper objectMapper) {
    return new IamPolicyReader(objectMapper);
  }

  @Bean
  @DependsOn("netflixAwsLambdaCredentials")
  public LambdaProvider lambdaProvider(AccountCredentialsRepository accountCredentialsRepository, AmazonClientProvider amazonClientProvider,
                                 AWSCredentialsProvider awsCredentialsProvider, Registry registry, IamPolicyReader iamPolicyReader,
                                 ObjectMapper objectMapper) {
    LambdaProvider provider = new LambdaProvider(accountCredentialsRepository, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));
    synchronizeAwsLambdaProvider(provider, accountCredentialsRepository, amazonClientProvider, awsCredentialsProvider, registry, iamPolicyReader, objectMapper);
    return provider;
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public AwsLambdaProviderSynchronizer synchronizeAwsLambdaProvider(LambdaProvider lambdaProvider, AccountCredentialsRepository accountCredentialsRepository,
                                                        AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry,
                                                        IamPolicyReader iamPolicyReader,
                                                        ObjectMapper objectMapper) {

    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(lambdaProvider);
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials.class);
    List<Agent> newAgents = new LinkedList<>();

    for (NetflixAmazonCredentials credentials : allAccounts) {
      if (credentials.getCloudProvider().equals(LambdaCloudProvider.ID)) {
        newAgents.add(new IamRoleCachingAgent(credentials, amazonClientProvider, awsCredentialsProvider, iamPolicyReader)); // IAM is region-agnostic, so one caching agent per account is enough

        for (AWSRegion region : credentials.getRegions()) {
          if (!scheduledAccounts.contains(credentials.getName())) {
            newAgents.add(new AmazonLambdaFunctionCachingAgent(amazonClientProvider, credentials, region.getName()));
          }
        }
      }
    }

    lambdaProvider.getAgents().addAll(newAgents);
    lambdaProvider.synchronizeHealthAgents();
    return new AwsLambdaProviderSynchronizer();
  }

  class AwsLambdaProviderSynchronizer {
  }
}
