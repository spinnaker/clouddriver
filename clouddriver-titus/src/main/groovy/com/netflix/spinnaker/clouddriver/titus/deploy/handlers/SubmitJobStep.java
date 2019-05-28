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
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubmitJobStep implements SagaStepFunction {

  private final RetrySupport retrySupport;

  public SubmitJobStep(RetrySupport retrySupport) {
    this.retrySupport = retrySupport;
  }

  /**
   * NOTE: The single-element array usage is to get around line-for-line Groovy conversion variable
   * references inside of the lambda. This should really be refactored so that pattern isn't
   * necessary. It's really gross as-is.
   */
  @Override
  public StepResult apply(SagaState sagaState) {
    final TitusClient titusClient = sagaState.getRequired("titusClient");
    final SubmitJobRequest submitJobRequest = sagaState.getRequired("submitJobRequest");
    final TitusDeployDescription description = sagaState.getRequired("description");
    final String[] nextServerGroupName = {sagaState.getRequired("nextServerGroupName")};

    final int[] retryCount = {0};
    String jobUri =
        retrySupport.retry(
            () -> {
              try {
                return titusClient.submitJob(submitJobRequest);
              } catch (StatusRuntimeException e) {
                if (isServiceExceptionRetryable(description, e)) {
                  String statusDescription = e.getStatus().getDescription();
                  if (statusDescription != null
                      && statusDescription.contains(
                          "Job sequence id reserved by another pending job")) {
                    try {
                      Thread.sleep(1000 ^ (int) Math.round(Math.pow(2, retryCount[0])));
                    } catch (InterruptedException ex) {
                      // Sweep this under the rug...
                    }
                    retryCount[0]++;
                  }
                  nextServerGroupName[0] =
                      regenerateJobName(sagaState, description, submitJobRequest, titusClient);
                  sagaState.appendLog(
                      "Retrying with %s after %s attempts", nextServerGroupName[0], retryCount[0]);
                  throw e;
                }
                if (isStatusCodeRetryable(e.getStatus().getCode())) {
                  retryCount[0]++;
                  sagaState.appendLog("Retrying after %s attempts", retryCount[0]);
                  throw e;
                } else {
                  log.error(
                      "Could not submit job and not retrying for status {}", e.getStatus(), e);
                  sagaState.appendLog("Could not submit job %s: %s", e.getStatus(), e.getMessage());
                  throw e;
                }
              }
            },
            8,
            100,
            true);

    if (jobUri == null) {
      throw new TitusException("could not create job");
    }

    sagaState.appendLog("Successfully submitted job request to Titus (Job URI: %s)", jobUri);

    Map<String, Object> newState = new HashMap<>();
    newState.put("nextServerGroupName", nextServerGroupName[0]);
    newState.put(
        "serverGroupNameByRegion",
        Collections.singletonMap(description.getRegion(), nextServerGroupName[0]));
    newState.put("jobUri", jobUri);

    return new DefaultStepResult(newState);
  }

  /**
   * TODO(rz): Not super stoked about this method existing here when virtually the same code exists
   * in PrepareDeploymentStep, but I'm also getting lazy.
   */
  private String regenerateJobName(
      SagaState state,
      TitusDeployDescription description,
      SubmitJobRequest submitJobRequest,
      TitusClient titusClient) {
    if (JobType.BATCH.value().equals(submitJobRequest.getJobType())) {
      submitJobRequest.withJobName(description.getApplication());
      return description.getApplication();
    }
    String nextServerGroupName;
    TitusServerGroupNameResolver serverGroupNameResolver =
        new TitusServerGroupNameResolver(titusClient, description.getRegion());
    if (description.getSequence() != null) {
      nextServerGroupName =
          serverGroupNameResolver.generateServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              description.getSequence(),
              false);
    } else {
      nextServerGroupName =
          serverGroupNameResolver.resolveNextServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              false);
    }
    submitJobRequest.withJobName(nextServerGroupName);

    state.appendLog("Resolved server group name to '%s'", nextServerGroupName);

    return nextServerGroupName;
  }

  private static boolean isServiceExceptionRetryable(
      TitusDeployDescription description, StatusRuntimeException e) {
    String statusDescription = e.getStatus().getDescription();
    return JobType.SERVICE.value().equals(description.getJobType())
        && (e.getStatus().getCode() == Status.RESOURCE_EXHAUSTED.getCode()
            || e.getStatus().getCode() == Status.INVALID_ARGUMENT.getCode())
        && (statusDescription != null
            && (statusDescription.contains("Job sequence id reserved by another pending job")
                || statusDescription.contains("Constraint violation - job with group sequence")));
  }

  private static boolean isStatusCodeRetryable(Status.Code code) {
    return code == Status.UNAVAILABLE.getCode()
        || code == Status.INTERNAL.getCode()
        || code == Status.DEADLINE_EXCEEDED.getCode();
  }
}
