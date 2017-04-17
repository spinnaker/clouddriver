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

package com.netflix.spinnaker.clouddriver.dcos.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

@Slf4j
@Configuration
class DcosCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Bean
  List<? extends DcosCredentials> dcosCredentials(String clouddriverUserAgentApplicationName,
                                                  DcosConfigurationProperties dcosConfigurationProperties,
                                                  ApplicationContext applicationContext,
                                                  AccountCredentialsRepository accountCredentialsRepository,
                                                  List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {

    synchronizeDcosAccounts(clouddriverUserAgentApplicationName, dcosConfigurationProperties, null, applicationContext, accountCredentialsRepository, providerSynchronizerTypeWrappers)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeDcosAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @DependsOn("dockerRegistryNamedAccountCredentials")
  List<? extends DcosCredentials> synchronizeDcosAccounts(String clouddriverUserAgentApplicationName,
                                                          DcosConfigurationProperties dcosConfigurationProperties,
                                                          CatsModule catsModule,
                                                          ApplicationContext applicationContext,
                                                          AccountCredentialsRepository accountCredentialsRepository,
                                                          List<ProviderSynchronizerTypeWrapper> providerSynchronizerTypeWrappers) {

    // TODO what to do with clouddriverUserAgentApplicationName?

    def (ArrayList<DcosConfigurationProperties.Account> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
      DcosCredentials,
      dcosConfigurationProperties.accounts)

    accountsToAdd.each { DcosConfigurationProperties.Account account ->
      try {

        def dcosCredentials = DcosCredentials.builder().name(account.name)
          .environment(account.environment)
          .accountType(account.accountType)
          .dcosUrl(account.dcosUrl)
          .dockerRegistries(account.dockerRegistries)
          .requiredGroupMembership(account.requiredGroupMembership)
          .secretStore(account.secretStore)
          .dcosClientConfig(DcosCredentialsInitializer.buildConfig(account))
          .build()

        accountCredentialsRepository.save(dcosCredentials.name, dcosCredentials)
      } catch (e) {
        log.info "Could not load account ${account.name} for DC/OS.", e
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    if (accountsToAdd && catsModule) {
      ProviderUtils.synchronizeAgentProviders(applicationContext, providerSynchronizerTypeWrappers)
    }

    accountCredentialsRepository.all.findAll {
      it instanceof DcosCredentials
    } as List<DcosCredentials>
  }

  private static Config buildConfig(final DcosConfigurationProperties.Account account) {
    Config.builder().withCredentials(buildDCOSAuthCredentials(account))
      .withInsecureSkipTlsVerify(account.insecureSkipTlsVerify)
      .withCaCertData(account.caCertData)
      .withCaCertFile(account.caCertFile).build()
  }

  private static DCOSAuthCredentials buildDCOSAuthCredentials(DcosConfigurationProperties.Account account) {
    DCOSAuthCredentials dcosAuthCredentials = null

    if (account.uid && account.password && account.serviceKey) {
      throw new IllegalStateException("Both a password and serviceKey were supplied for the account with name [${account.name}]. Only one should be configured.")
    } else if (account.uid && account.password) {
      dcosAuthCredentials = DCOSAuthCredentials.forUserAccount(account.uid, account.password)
    } else if (account.uid && account.serviceKey) {
      dcosAuthCredentials = DCOSAuthCredentials.forServiceAccount(account.uid, account.serviceKey)
    }

    dcosAuthCredentials
  }
}
