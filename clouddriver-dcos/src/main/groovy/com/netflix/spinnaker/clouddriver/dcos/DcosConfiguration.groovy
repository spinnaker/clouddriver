/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.dcos.health.DcosHealthIndicator
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import groovy.transform.ToString
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
  @Bean
  @ConfigurationProperties("dcos")
  DcosCredentialsConfig dcosCredentialsConfig() {
    new DcosCredentialsConfig()
  }

  @Bean
  List<DcosCredentials> dcosCredentials(DcosCredentialsConfig titusCredentialsConfig,
                                        AccountCredentialsRepository repository) {
    List<DcosCredentials> accounts = new ArrayList<>()
    for (DcosCredentialsConfig.Account account in titusCredentialsConfig.accounts) {
      DcosCredentials credentials = new DcosCredentials(account.name, account.group, account.environment, account.accountType, account.instance, account.user, account.password)
      accounts.add(credentials)
      repository.save(account.name, credentials)
    }
    return accounts
  }

  @Bean
  DcosClientProvider dcosClientProvider(Registry registry) {
    return new DcosClientProvider(registry)
  }

  @Bean
  DcosHealthIndicator dcosHealthIndicator(AccountCredentialsProvider accountCredentialsProvider, DcosClientProvider dcosClientProvider) {
    new DcosHealthIndicator(accountCredentialsProvider, dcosClientProvider)
  }

  static class DcosCredentialsConfig {
    List<Account> accounts = []

    @ToString(includeNames = true)
    static class Account {
      String name
      String group
      String environment
      String accountType
      String instance
      String user
      String password
    }
  }
}


