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

import com.netflix.spinnaker.clouddriver.saga.DefaultStepResult;
import com.netflix.spinnaker.clouddriver.saga.SagaStepFunction;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription;
import com.netflix.titus.grpc.protogen.PutPolicyRequest;
import com.netflix.titus.grpc.protogen.ScalingPolicyResult;
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CopyScalingPoliciesStep extends AbstractTitusDeployStep implements SagaStepFunction {

  private static final List<ScalingPolicyStatus.ScalingPolicyState> IGNORED_STATES =
      Arrays.asList(
          ScalingPolicyStatus.ScalingPolicyState.Deleted,
          ScalingPolicyStatus.ScalingPolicyState.Deleting);

  CopyScalingPoliciesStep(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider) {
    super(accountCredentialsRepository, titusClientProvider);
  }

  @Override
  public StepResult apply(SagaState state) {
    TitusDeployDescription description = state.getRequired("description");

    // TODO(rz): Should have `state.getRequired(key)` or something that doesn't return a @Nullable.
    if (!description.isCopySourceScalingPolicies()
        || !description.getCopySourceScalingPoliciesAndActions()) {
      return new DefaultStepResult();
    }

    TitusDeployDescription.Source source = description.getSource();
    TitusClient sourceClient = buildSourceTitusClient(source);
    if (sourceClient == null) {
      // No source, no copying.
      return new DefaultStepResult();
    }

    TitusAutoscalingClient autoscalingClient =
        titusClientProvider.getTitusAutoscalingClient(
            description.getCredentials(), description.getRegion());
    if (autoscalingClient == null) {
      state.appendLog(
          "Unable to create client in target account/region; policies will not be copied");
      return new DefaultStepResult();
    }

    TitusAutoscalingClient sourceAutoscalingClient = buildSourceAutoscalingClient(source);
    if (sourceAutoscalingClient == null) {
      state.appendLog(
          "Unable to create client in source account/region; policies will not be copied");
      return new DefaultStepResult();
    }

    Job sourceJob = sourceClient.findJobByName(source.getAsgName());
    if (sourceJob == null) {
      state.appendLog(
          "Unable to locate source (%s:%s:%s)",
          source.getAccount(), source.getRegion(), source.getAsgName());
    } else {
      String jobUri = state.get("jobUri");
      String serverGroupName = state.get("nextServerGroupName");

      state.appendLog("Copying scaling policies from source (Job URI: %s)", jobUri);
      List<ScalingPolicyResult> policies =
          Optional.ofNullable(sourceAutoscalingClient.getJobScalingPolicies(sourceJob.getId()))
              .orElse(Collections.emptyList());
      state.appendLog(
          "Found %d scaling policies for source (Job URI: %s)", policies.size(), jobUri);
      policies.forEach(
          policy -> {
            if (!IGNORED_STATES.contains(policy.getPolicyState().getState())) {
              PutPolicyRequest.Builder builder =
                  PutPolicyRequest.newBuilder()
                      .setJobId(jobUri)
                      .setScalingPolicy(
                          UpsertTitusScalingPolicyDescription.fromScalingPolicyResult(
                                  description.getRegion(), policy, serverGroupName)
                              .toScalingPolicyBuilder());
              state.appendLog("Creating new scaling policy copied from policy %s", policy.getId());

              autoscalingClient.createScalingPolicy(builder.build());
            }
          });
    }

    state.appendLog("Copy scaling policies completed");

    return new DefaultStepResult();
  }

  private TitusAutoscalingClient buildSourceAutoscalingClient(
      TitusDeployDescription.Source source) {
    if (!isNullOrEmpty(source.getAccount())
        && !isNullOrEmpty(source.getRegion())
        && !isNullOrEmpty(source.getAsgName())) {
      AccountCredentials sourceCredentials =
          accountCredentialsRepository.getOne(source.getAccount());

      TitusUtils.assertTitusAccountCredentialsType(sourceCredentials);

      return titusClientProvider.getTitusAutoscalingClient(
          (NetflixTitusCredentials) sourceCredentials, source.getRegion());
    }

    throw new PrepareDeploymentStep.InsufficientDeploySourceStateException(
        "Could not create titus client from deployment Source",
        source.getAccount(),
        source.getRegion(),
        source.getAsgName());
  }
}
