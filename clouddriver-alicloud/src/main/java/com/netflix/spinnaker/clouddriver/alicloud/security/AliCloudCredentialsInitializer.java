/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.security;

import com.aliyuncs.auth.AlibabaCloudCredentialsProvider;
import com.aliyuncs.auth.BasicCredentials;
import com.aliyuncs.auth.EnvironmentVariableCredentialsProvider;
import com.aliyuncs.auth.STSAssumeRoleSessionCredentialsProvider;
import com.aliyuncs.auth.StaticCredentialsProvider;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.utils.AuthUtils;
import com.netflix.spinnaker.clouddriver.alicloud.security.config.AliCloudAccountConfig;
import com.netflix.spinnaker.clouddriver.alicloud.security.config.AliCloudAccountConfig.Account;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AliCloudCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("alicloud")
  AliCloudAccountConfig aliCloudAccountConfig() {
    return new AliCloudAccountConfig();
  }

  @Bean
  List synchronizeAliCloudAccounts(
      AliCloudAccountConfig aliCloudAccountConfig,
      AccountCredentialsRepository accountCredentialsRepository) {
    List<AliCloudCredentials> aliCloudCredentialsList = new ArrayList<>();

    if (StringUtils.isNotBlank(aliCloudAccountConfig.getAccessKeyId())
        && StringUtils.isNotBlank(aliCloudAccountConfig.getAccessSecretKey())) {
      AuthUtils.setEnvironmentAccessKeyId(aliCloudAccountConfig.getAccessKeyId());
      AuthUtils.setEnvironmentAccessKeySecret(aliCloudAccountConfig.getAccessSecretKey());
    }

    aliCloudAccountConfig.getAccounts().stream()
        .forEach(
            account -> {
              AlibabaCloudCredentialsProvider provider =
                  buildProvider(account, aliCloudAccountConfig.getDefaultRegion());

              AliCloudCredentials aliCloudCredentials =
                  new AliCloudCredentials(
                      account.getName(),
                      account.getEnvironment(),
                      account.getAccountType(),
                      account.getRegions(),
                      account.getRequiredGroupMembership(),
                      provider);
              accountCredentialsRepository.save(account.getName(), aliCloudCredentials);
              aliCloudCredentialsList.add(aliCloudCredentials);
            });
    return aliCloudCredentialsList;
  }

  private AlibabaCloudCredentialsProvider buildProvider(Account account, String defaultRegion) {
    AlibabaCloudCredentialsProvider provider = new EnvironmentVariableCredentialsProvider();

    if (StringUtils.isNotBlank(account.getAssumeRole())) {

      String roleArn =
          String.format("acs:ram::%s:%s", account.getAccountId(), account.getAssumeRole());

      if (StringUtils.isNotBlank(account.getAccessKeyId())
          && StringUtils.isNotBlank(account.getAccessSecretKey())) {
        return new STSAssumeRoleSessionCredentialsProvider(
            new StaticCredentialsProvider(
                new BasicCredentials(account.getAccessKeyId(), account.getAccessSecretKey())),
            roleArn,
            DefaultProfile.getProfile(defaultRegion));
      } else {
        return new STSAssumeRoleSessionCredentialsProvider(
            provider, roleArn, DefaultProfile.getProfile(defaultRegion));
      }
    } else {
      if (StringUtils.isNotBlank(account.getAccessKeyId())
          && StringUtils.isNotBlank(account.getAccessSecretKey())) {
        return new StaticCredentialsProvider(
            new BasicCredentials(account.getAccessKeyId(), account.getAccessSecretKey()));
      }
    }
    return provider;
  }
}
