/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import static com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties.ManagedAccount;
import static com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials.CredentialFactory;

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesCredentialsSynchronizer implements CredentialsInitializerSynchronizable {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;
  private final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;
  private final CredentialFactory credentialFactory;
  private final CatsModule catsModule;

  public KubernetesCredentialsSynchronizer(
      AccountCredentialsRepository accountCredentialsRepository,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      CredentialFactory credentialFactory,
      CatsModule catsModule) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;
    this.credentialFactory = credentialFactory;
    this.catsModule = catsModule;
  }

  @Override
  @PostConstruct
  public void synchronize() {

    List accountDelta =
        ProviderUtils.calculateAccountDeltas(
            accountCredentialsRepository,
            KubernetesNamedAccountCredentials.class,
            kubernetesConfigurationProperties.getAccounts());

    List<ManagedAccount> accountsToAdd = (List<ManagedAccount>) accountDelta.get(0);
    List<String> deletedAccounts = (List<String>) accountDelta.get(1);

    ProviderUtils.unscheduleAndDeregisterAgents(deletedAccounts, catsModule);

    accountsToAdd.forEach(
        managedAccount -> {
          try {
            KubernetesNamedAccountCredentials credentials =
                new KubernetesNamedAccountCredentials(
                    managedAccount, kubernetesSpinnakerKindMap, credentialFactory);
            accountCredentialsRepository.save(managedAccount.getName(), credentials);
          } catch (Exception e) {
            log.info("Could not load account {} for Kubernetes", managedAccount.getName());
          }
        });
  }
}
