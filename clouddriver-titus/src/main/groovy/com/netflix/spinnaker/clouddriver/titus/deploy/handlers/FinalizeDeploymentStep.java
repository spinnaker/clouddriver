/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.UnionSagaEvent3;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCompleted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancersApplied;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPoliciesApplied;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class FinalizeDeploymentStep
    implements SagaEventHandler<
        UnionSagaEvent3<
            TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>> {

  private final AccountCredentialsProvider accountCredentialsProvider;

  public FinalizeDeploymentStep(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @NotNull
  @Override
  public List<SagaEvent> apply(
      @NotNull
          UnionSagaEvent3<TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>
              event,
      @NotNull Saga saga) {
    final TitusDeployDescription description = event.getA().getDescription();
    final String nextServerGroupName = event.getA().getNextServerGroupName();
    final String jobUri = event.getA().getJobUri();

    TitusDeploymentResult deploymentResult = new TitusDeploymentResult();

    if (JobType.BATCH.value().equals(description.getJobType())) {
      deploymentResult.setDeployedNames(Collections.emptyList());
      deploymentResult.setDeployedNamesByLocation(
          Collections.singletonMap(description.getRegion(), Collections.singletonList(jobUri)));
      deploymentResult.setJobUri(jobUri);
    } else {
      deploymentResult.setServerGroupNames(
          Collections.singletonList(format("%s:%s", description.getRegion(), nextServerGroupName)));
      deploymentResult.setServerGroupNameByRegion(
          Collections.singletonMap(description.getRegion(), nextServerGroupName));
      deploymentResult.setJobUri(jobUri);
    }

    deploymentResult.setMessages(saga.getLogs());

    description
        .getEvents()
        .add(
            new CreateServerGroupEvent(
                TitusCloudProvider.ID,
                TitusUtils.getAccountId(accountCredentialsProvider, description.getAccount()),
                description.getRegion(),
                nextServerGroupName));

    return Collections.singletonList(
        new TitusDeployCompleted(saga.getName(), saga.getId(), deploymentResult));
  }

  @Override
  public void compensate(
      @NotNull
          UnionSagaEvent3<TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>
              event,
      @NotNull Saga saga) {}

  @Override
  public void finalize(
      @NotNull
          UnionSagaEvent3<TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>
              event,
      @NotNull Saga saga) {}
}
