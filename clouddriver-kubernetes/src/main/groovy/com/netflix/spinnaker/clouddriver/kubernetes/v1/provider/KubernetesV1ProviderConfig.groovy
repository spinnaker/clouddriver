/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent.KubernetesV1CachingAgentDispatcher
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class KubernetesV1ProviderConfig {

  private KubernetesV1Provider kubernetesV1Provider
  private KubernetesCloudProvider kubernetesCloudProvider
  private AccountCredentialsRepository accountCredentialsRepository
  private KubernetesV1CachingAgentDispatcher kubernetesV1CachingAgentDispatcher

  KubernetesV1ProviderConfig(
    KubernetesV1Provider kubernetesV1Provider,
    KubernetesCloudProvider kubernetesCloudProvider,
    AccountCredentialsRepository accountCredentialsRepository,
    KubernetesV1CachingAgentDispatcher kubernetesV1CachingAgentDispatcher
  ) {
    this.kubernetesV1Provider = kubernetesV1Provider
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.kubernetesV1CachingAgentDispatcher = kubernetesV1CachingAgentDispatcher
  }

  void synchronizeKubernetesV1Provider() {
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, KubernetesNamedAccountCredentials, ProviderVersion.v1)

    kubernetesV1Provider.agents.clear()

    for (KubernetesNamedAccountCredentials credentials : allAccounts) {
      def newlyAddedAgents = kubernetesV1CachingAgentDispatcher.buildAllCachingAgents(credentials)

      log.info "Adding ${newlyAddedAgents.size()} agents for account ${credentials.name}"

      // If there is an agent scheduler, then this provider has been through the AgentController in the past.
      // In that case, we need to do the scheduling here (because accounts have been added to a running system).
      if (kubernetesV1Provider.agentScheduler) {
        ProviderUtils.rescheduleAgents(kubernetesV1Provider, newlyAddedAgents)
      }

      kubernetesV1Provider.agents.addAll(newlyAddedAgents)
    }
  }
}
