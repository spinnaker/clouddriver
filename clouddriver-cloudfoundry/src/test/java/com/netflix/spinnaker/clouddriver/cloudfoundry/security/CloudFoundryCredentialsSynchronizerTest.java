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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudFoundryCredentialsSynchronizerTest {
  private CloudFoundryCredentialsSynchronizer synchronizer;

  private final CloudFoundryConfigurationProperties configurationProperties =
      new CloudFoundryConfigurationProperties();
  private final AccountCredentialsRepository repository =
      new MapBackedAccountCredentialsRepository();

  private final CloudFoundryProvider provider = new CloudFoundryProvider(new ArrayList<>());
  private final TestAgentScheduler scheduler = new TestAgentScheduler();

  private final CatsModule catsModule = mock(CatsModule.class);
  private final ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
  private final Registry registry = mock(Registry.class);
  private final CacheRepository cacheRepository = mock(CacheRepository.class);

  @BeforeEach
  void setUp() {
    when(catsModule.getProviderRegistry()).thenReturn(providerRegistry);
    when(providerRegistry.getProviders()).thenReturn(Collections.singletonList(provider));

    provider.setAgentScheduler(scheduler);

    synchronizer =
        new CloudFoundryCredentialsSynchronizer(
            provider, configurationProperties, repository, catsModule, registry, cacheRepository);
  }

  private class StaticOtherProviderCredentials extends AbstractAccountCredentials<Void> {
    @Override
    public String getName() {
      return "unchanged-other-provider";
    }

    @Override
    public String getEnvironment() {
      return null;
    }

    @Override
    public String getAccountType() {
      return null;
    }

    @Override
    public Void getCredentials() {
      return null;
    }

    @Override
    public String getCloudProvider() {
      return "other";
    }

    @Override
    public List<String> getRequiredGroupMembership() {
      return null;
    }
  }

  @Test
  void synchronize() {
    repository.save("to-be-changed", createCredentials("to-be-changed"));
    repository.save("unchanged2", createCredentials("unchanged2"));
    repository.save("unchanged3", createCredentials("unchanged3"));
    repository.save("to-be-deleted", createCredentials("to-be-deleted"));
    repository.save("unchanged-other-provider", new StaticOtherProviderCredentials());

    loadProviderFromRepository();

    CloudFoundryConfigurationProperties.ManagedAccount changedAccount =
        createAccount("to-be-changed");
    changedAccount.setPassword("newpassword");

    configurationProperties.setAccounts(
        Arrays.asList(
            createAccount("unchanged2"),
            createAccount("unchanged3"),
            createAccount("added"),
            changedAccount));

    synchronizer.synchronize();

    assertThat(repository.getAll())
        .extracting(AccountCredentials::getName)
        .containsExactlyInAnyOrder(
            "unchanged2", "unchanged3", "added", "to-be-changed", "unchanged-other-provider");

    assertThat(ProviderUtils.getScheduledAccounts(provider))
        .containsExactlyInAnyOrder("unchanged2", "unchanged3", "added", "to-be-changed");

    assertThat(scheduler.getScheduledAccountNames())
        .containsExactlyInAnyOrder(
            "added", "added", "added", "to-be-changed", "to-be-changed", "to-be-changed");
    assertThat(scheduler.getUnscheduledAccountNames())
        .containsExactlyInAnyOrder("to-be-changed", "to-be-deleted");
  }

  private CloudFoundryConfigurationProperties.ManagedAccount createAccount(String name) {
    CloudFoundryConfigurationProperties.ManagedAccount account =
        new CloudFoundryConfigurationProperties.ManagedAccount();
    account.setName(name);
    account.setApi("api." + name);
    account.setUser("user-" + name);
    account.setPassword("pwd-" + name);

    return account;
  }

  private CloudFoundryCredentials createCredentials(String name) {
    return new CloudFoundryCredentials(
        name,
        null,
        null,
        "api." + name,
        "user-" + name,
        "pwd-" + name,
        null,
        false,
        null,
        16,
        cacheRepository,
        null);
  }

  private void loadProviderFromRepository() {
    Set<CloudFoundryCredentials> accounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(repository, CloudFoundryCredentials.class);

    List<CloudFoundryServerGroupCachingAgent> agents =
        accounts.stream()
            .map(account -> new CloudFoundryServerGroupCachingAgent(account, registry))
            .collect(Collectors.toList());

    provider.getAgents().addAll(agents);
  }

  private class TestAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock> {
    private List<String> scheduledAccountNames = new ArrayList<>();
    private List<String> unscheduledAccountNames = new ArrayList<>();

    @Override
    public void schedule(
        Agent agent,
        AgentExecution agentExecution,
        ExecutionInstrumentation executionInstrumentation) {
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
