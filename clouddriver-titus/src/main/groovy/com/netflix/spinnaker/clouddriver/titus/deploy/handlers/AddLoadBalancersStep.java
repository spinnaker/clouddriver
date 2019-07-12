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
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancersApplied;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AddLoadBalancersStep implements SagaEventHandler<TitusJobSubmitted> {

  private final TitusClientProvider titusClientProvider;

  public AddLoadBalancersStep(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public List<SagaEvent> apply(@NotNull TitusJobSubmitted event, @NotNull Saga saga) {
    final TitusDeployDescription description = event.getDescription();

    if (JobType.BATCH.value().equals(description.getJobType())) {
      // Batch jobs don't get load balancers
      return Collections.emptyList();
    }

    TitusLoadBalancerClient loadBalancerClient =
        titusClientProvider.getTitusLoadBalancerClient(
            description.getCredentials(), description.getRegion());
    if (loadBalancerClient == null) {
      // TODO(rz): This definitely doesn't seem like something to casually skip?
      saga.log("Unable to create load balancing client in target account/region");
      return Collections.emptyList();
    }

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroups =
        event.getTargetGroupLookupResult();
    if (targetGroups == null) {
      return Collections.emptyList();
    }

    String jobUri = event.getJobUri();

    targetGroups
        .getTargetGroupARNs()
        .forEach(
            targetGroupArn -> {
              loadBalancerClient.addLoadBalancer(jobUri, targetGroupArn);
              saga.log("Attached %s to %s", targetGroupArn, jobUri);
            });

    saga.log("Load balancers applied");

    return Collections.singletonList(new TitusLoadBalancersApplied(saga.getName(), saga.getId()));
  }

  @Override
  public void compensate(@NotNull TitusJobSubmitted event, @NotNull Saga saga) {}

  @Override
  public void finalize(@NotNull TitusJobSubmitted event, @NotNull Saga saga) {}
}
