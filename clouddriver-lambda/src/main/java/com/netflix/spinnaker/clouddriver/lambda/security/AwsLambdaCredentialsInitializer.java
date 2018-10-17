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

package com.netflix.spinnaker.clouddriver.lambda.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Configuration
public class AwsLambdaCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("awslambda")
  public AwsLambdaCredentialsConfig awsLambdaCredentialsConfig() {
    return new AwsLambdaCredentialsConfig();
  }

  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> netflixAwsLambdaCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                        AwsLambdaCredentialsConfig credentialsConfig,
                                                                        AccountCredentialsRepository accountCredentialsRepository) throws Throwable {
    return synchronizeAwsLambdaAccounts(credentialsLoader, credentialsConfig, accountCredentialsRepository);
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("netflixAmazonCredentials")
  public List<? extends NetflixAmazonCredentials> synchronizeAwsLambdaAccounts(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                         AwsLambdaCredentialsConfig awslambdaCredentialsConfig,
                                                                         AccountCredentialsRepository accountCredentialsRepository) throws Throwable {

    // TODO: add support for mutable accounts.
    //List deltaAccounts = ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAmazonCredentials.class, accounts);
    List<NetflixAmazonCredentials> credentials = new LinkedList<>();

    for (AccountCredentials accountCredentials : accountCredentialsRepository.getAll()) {
      if (accountCredentials instanceof NetflixAmazonCredentials) {
        for (AwsLambdaCredentialsConfig.Account awslambdaAccount : awslambdaCredentialsConfig.getAccounts()) {
          if (awslambdaAccount.getAwsAccount().equals(accountCredentials.getName())) {

            NetflixAmazonCredentials netflixAmazonCredentials = (NetflixAmazonCredentials) accountCredentials;

            // TODO: accountCredentials should be serializable or somehow cloneable.
            CredentialsConfig.Account account = AwsLambdaAccountBuilder.build(netflixAmazonCredentials, awslambdaAccount.getName(), "awslambda");

            CredentialsConfig awslambdaCopy = new CredentialsConfig();
            awslambdaCopy.setAccounts(Collections.singletonList(account));

            NetflixAwsLambdaCredentials awslambdaCredentials = new NetflixAssumeRoleAwsLambdaCredentials((NetflixAssumeRoleAmazonCredentials)credentialsLoader.load(awslambdaCopy).get(0), awslambdaAccount.getAwsAccount());
            credentials.add(awslambdaCredentials);

            accountCredentialsRepository.save(awslambdaAccount.getName(), awslambdaCredentials);
            break;

          }
        }
      }
    }

    return credentials;
  }

  @Override
  public String getCredentialsSynchronizationBeanName() {
    return "synchronizeAwsLambdaAccounts";
  }
}
