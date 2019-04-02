/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider;

import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryCachingAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CloudFoundryProviderSynchronizer implements ProviderSynchronizable {

  private final CloudFoundryProvider cloudFoundryProvider;
  private final AccountCredentialsRepository accountCredentialsRepository;

  public CloudFoundryProviderSynchronizer(CloudFoundryProvider cloudFoundryProvider,
                                          AccountCredentialsRepository accountCredentialsRepository) {
    this.cloudFoundryProvider = cloudFoundryProvider;
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  @Override
  @PostConstruct
  public void synchronize() {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(cloudFoundryProvider);
    Set<CloudFoundryCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
      CloudFoundryCredentials.class);

    cloudFoundryProvider.getAgents().addAll(allAccounts.stream()
      .map(credentials -> !scheduledAccounts.contains(credentials.getName()) ?
        new CloudFoundryCachingAgent(credentials.getName(), credentials.getClient()) :
        null)
      .filter(Objects::nonNull)
      .collect(Collectors.toList()));
  }
}
