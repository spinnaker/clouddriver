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

import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.saga.DefaultStepResult;
import com.netflix.spinnaker.clouddriver.saga.SagaStepFunction;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;

public class AddLoadBalancersStep implements SagaStepFunction {

  private final TitusClientProvider titusClientProvider;

  public AddLoadBalancersStep(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider;
  }

  @Override
  public StepResult apply(SagaState state) {
    TitusDeployDescription description = state.getRequired("description");

    if (JobType.BATCH.value().equals(description.getJobType())) {
      // Batch jobs don't get load balancers
      return new DefaultStepResult();
    }

    TitusLoadBalancerClient loadBalancerClient =
        titusClientProvider.getTitusLoadBalancerClient(
            description.getCredentials(), description.getRegion());
    if (loadBalancerClient == null) {
      // TODO(rz): This definitely doesn't seem like something to casually skip?
      state.appendLog("Unable to create load balancing client in target account/region");
      return new DefaultStepResult();
    }

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroups =
        state.get("targetGroupLookupResult");

    if (targetGroups == null) {
      return new DefaultStepResult();
    }

    String jobUri = state.get("jobUri");

    targetGroups
        .getTargetGroupARNs()
        .forEach(
            targetGroupArn -> {
              loadBalancerClient.addLoadBalancer(jobUri, targetGroupArn);
              state.appendLog("Attached %s to %s", targetGroupArn, jobUri);
            });

    return new DefaultStepResult();
  }
}
