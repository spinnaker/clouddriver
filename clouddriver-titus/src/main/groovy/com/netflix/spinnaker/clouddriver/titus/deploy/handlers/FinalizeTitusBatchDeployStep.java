/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCompleted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FinalizeTitusBatchDeployStep implements SagaEventHandler<TitusJobSubmitted> {

  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public FinalizeTitusBatchDeployStep(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @NotNull
  @Override
  public List<SagaEvent> apply(@NotNull TitusJobSubmitted event, @NotNull Saga saga) {
    final TitusDeployDescription description = event.getDescription();
    final String jobUri = event.getJobUri();

    TitusDeploymentResult deploymentResult = new TitusDeploymentResult();

    deploymentResult.setDeployedNames(Collections.emptyList());
    deploymentResult.setDeployedNamesByLocation(
        Collections.singletonMap(description.getRegion(), Collections.singletonList(jobUri)));
    deploymentResult.setJobUri(jobUri);
    deploymentResult.setMessages(saga.getLogs());

    return Collections.singletonList(
        new TitusDeployCompleted(
            saga.getName(),
            saga.getId(),
            deploymentResult,
            TitusUtils.getAccountId(accountCredentialsProvider, description.getAccount())));
  }

  @Override
  public void compensate(@NotNull TitusJobSubmitted event, @NotNull Saga saga) {}
}
