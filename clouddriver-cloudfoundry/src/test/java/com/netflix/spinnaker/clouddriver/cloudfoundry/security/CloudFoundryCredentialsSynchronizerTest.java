/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CloudFoundryCredentialsSynchronizerTest {
  private CloudFoundryCredentialsSynchronizer synchronizer;

  private final CloudFoundryConfigurationProperties configurationProperties =
    new CloudFoundryConfigurationProperties();
  private final AccountCredentialsRepository repository = new MapBackedAccountCredentialsRepository();

  private final CloudFoundryProvider provider = new CloudFoundryProvider(new ArrayList<>());
  private final TestAgentScheduler scheduler = new TestAgentScheduler();

  private final CatsModule catsModule = mock(CatsModule.class);
  private final ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
  private final Registry registry = mock(Registry.class);

  @BeforeEach
  void setUp() {
    when(catsModule.getProviderRegistry()).thenReturn(providerRegistry);
    when(providerRegistry.getProviders()).thenReturn(Collections.singletonList(provider));

    provider.setAgentScheduler(scheduler);

    synchronizer = new CloudFoundryCredentialsSynchronizer(provider, configurationProperties,
      repository, catsModule, registry);
  }

  @Test
  void synchronizeWhenUpdating() {
    repository.save("test1", createCredentials(1));

    configurationProperties.setAccounts(Collections.singletonList(createAccount(1)));

    synchronizer.synchronize();

    assertThat(repository.getAll()).hasSize(1);
    assertThat(repository.getOne("test1")).isNotNull().hasFieldOrPropertyWithValue("environment", "updated");
  }

  @Test
  void synchronizeWhenAddingAndDeleting() {
    repository.save("test1", createCredentials(1));
    repository.save("test2", createCredentials(2));
    repository.save("test3", createCredentials(3));
    repository.save("test4", createCredentials(4));

    loadProviderFromRepository();

    configurationProperties.setAccounts(Arrays.asList(
      createAccount(2),
      createAccount(3),
      createAccount(5)));

    synchronizer.synchronize();

    assertThat(repository.getAll()).hasSize(3);
    assertThat(repository.getAll())
      .extracting(AccountCredentials::getName)
      .containsExactlyInAnyOrder("test2", "test3", "test5");

    assertThat(provider.getAgents()).hasSize(3);
    assertThat(ProviderUtils.getScheduledAccounts(provider))
      .containsExactlyInAnyOrder("test2", "test3", "test5");

    assertThat(scheduler.getScheduledAccountNames()).containsExactly("test5");
    assertThat(scheduler.getUnscheduledAccountNames()).containsExactlyInAnyOrder("test1", "test4");
  }

  private CloudFoundryConfigurationProperties.ManagedAccount createAccount(int count) {
    CloudFoundryConfigurationProperties.ManagedAccount account = new CloudFoundryConfigurationProperties.ManagedAccount();
    account.setName("test" + count);
    account.setApi("api.test" + count);
    account.setEnvironment("updated");

    return account;
  }

  private CloudFoundryCredentials createCredentials(int count) {
    return new CloudFoundryCredentials("test" + count, "", "", "", "", "", "existing");
  }

  private void loadProviderFromRepository() {
    Set<CloudFoundryCredentials> accounts = ProviderUtils.buildThreadSafeSetOfAccounts(repository,
      CloudFoundryCredentials.class);

    List<CloudFoundryCachingAgent> agents = accounts.stream()
      .map(account -> new CloudFoundryCachingAgent(account.getName(), account.getClient(), registry))
      .collect(Collectors.toList());

    provider.getAgents().addAll(agents);
  }

  private class TestAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock> {
    private List<String> scheduledAccountNames = new ArrayList<>();
    private List<String> unscheduledAccountNames = new ArrayList<>();

    @Override
    public void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
      if (agent instanceof AccountAware) {
        scheduledAccountNames.add(((AccountAware) agent).getAccountName());
      }
    }

    @Override
    public CatsModule getCatsModule() {
      return catsModule;
    }

    @Override
    public void unschedule(Agent agent) {
      if (agent instanceof AccountAware) {
        unscheduledAccountNames.add(((AccountAware) agent).getAccountName());
      }
    }

    List<String> getScheduledAccountNames() {
      return scheduledAccountNames;
    }

    List<String> getUnscheduledAccountNames() {
      return unscheduledAccountNames;
    }
  }
}
