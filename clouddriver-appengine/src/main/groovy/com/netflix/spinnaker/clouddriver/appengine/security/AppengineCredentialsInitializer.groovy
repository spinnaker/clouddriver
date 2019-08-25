/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.config.AppengineConfigurationProperties

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class AppengineCredentialsInitializer {
  @Bean
  List<? extends AppengineNamedAccountCredentials> appengineNamedAccountCredentials(
    String clouddriverUserAgentApplicationName,
    AppengineConfigurationProperties appengineConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository,
    AppengineJobExecutor jobExecutor,
    ConfigFileService configFileService) {

    synchronizeAppengineAccounts(clouddriverUserAgentApplicationName,
                                 appengineConfigurationProperties,
                                 null,
                                 accountCredentialsRepository,
                                 jobExecutor,
                                 configFileService)
  }

  private List<? extends AppengineNamedAccountCredentials> synchronizeAppengineAccounts(
    String clouddriverUserAgentApplicationName,
    AppengineConfigurationProperties appengineConfigurationProperties,
    CatsModule catsModule,
    AccountCredentialsRepository accountCredentialsRepository,
    AppengineJobExecutor jobExecutor,
    ConfigFileService configFileService) {

    def (ArrayList<AppengineConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
      ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
                                           AppengineNamedAccountCredentials,
                                           appengineConfigurationProperties.accounts)

    accountsToAdd.each { AppengineConfigurationProperties.ManagedAccount managedAccount ->
      try {
        String gcloudPath = appengineConfigurationProperties.gcloudPath
        if (StringUtils.isEmpty(gcloudPath)) {
          gcloudPath = "gcloud"
        }
        managedAccount.initialize(jobExecutor, gcloudPath)

        def jsonKey = configFileService.getContents(managedAccount.getJsonPath())
        def appengineAccount = new AppengineNamedAccountCredentials.Builder()
          .name(managedAccount.name)
          .environment(managedAccount.environment ?: managedAccount.name)
          .accountType(managedAccount.accountType ?: managedAccount.name)
          .project(managedAccount.project)
          .jsonKey(jsonKey)
          .applicationName(clouddriverUserAgentApplicationName)
          .gcloudPath(gcloudPath)
          .jsonPath(managedAccount.jsonPath)
          .requiredGroupMembership(managedAccount.requiredGroupMembership)
          .permissions(managedAccount.permissions.build())
          .serviceAccountEmail(managedAccount.serviceAccountEmail)
          .localRepositoryDirectory(managedAccount.localRepositoryDirectory)
          .gitHttpsUsername(managedAccount.gitHttpsUsername)
          .gitHttpsPassword(managedAccount.gitHttpsPassword)
          .githubOAuthAccessToken(managedAccount.githubOAuthAccessToken)
          .sshPrivateKeyFilePath(managedAccount.sshPrivateKeyFilePath)
          .sshPrivateKeyPassphrase(managedAccount.sshPrivateKeyPassphrase)
          .sshKnownHostsFilePath(managedAccount.sshKnownHostsFilePath)
          .sshTrustUnknownHosts(managedAccount.sshTrustUnknownHosts)
          .gcloudReleaseTrack(managedAccount.gcloudReleaseTrack)
          .services(managedAccount.services)
          .versions(managedAccount.versions)
          .omitServices(managedAccount.omitServices)
          .omitVersions(managedAccount.omitVersions)
          .cachingIntervalSeconds(managedAccount.cachingIntervalSeconds)
          .build()

        accountCredentialsRepository.save(managedAccount.name, appengineAccount)
      } catch (e) {
        log.info("Could not load account $managedAccount.name for App Engine", e)
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof AppengineNamedAccountCredentials
    } as List<AppengineNamedAccountCredentials>
  }
}
