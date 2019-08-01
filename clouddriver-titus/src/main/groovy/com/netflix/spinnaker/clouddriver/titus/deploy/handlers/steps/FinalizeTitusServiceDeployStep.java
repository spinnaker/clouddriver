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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers.steps;

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.UnionSagaEvent3;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCompleted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancersApplied;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPoliciesApplied;
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeploymentResult;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FinalizeTitusServiceDeployStep
    implements SagaEventHandler<
        UnionSagaEvent3<
            TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>> {

  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public FinalizeTitusServiceDeployStep(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @NotNull
  @Override
  public List<SagaEvent> apply(
      @NotNull
          UnionSagaEvent3<TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>
              event,
      @NotNull Saga saga) {
    if (!JobType.isEqual(event.getA().getDescription().getJobType(), JobType.SERVICE)) {
      return Collections.emptyList();
    }

    return Collections.singletonList(
        new TitusDeployCompleted(
            saga.getName(),
            saga.getId(),
            TitusDeploymentResult.from(event.getA(), saga.getLogs()),
            TitusUtils.getAccountId(
                accountCredentialsProvider, event.getA().getDescription().getAccount())));
  }

  @Override
  public void compensate(
      @NotNull
          UnionSagaEvent3<TitusJobSubmitted, TitusScalingPoliciesApplied, TitusLoadBalancersApplied>
              event,
      @NotNull Saga saga) {}
}
