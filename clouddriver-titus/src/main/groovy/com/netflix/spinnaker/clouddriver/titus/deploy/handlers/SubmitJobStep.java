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

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployPrepared;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class SubmitJobStep implements SagaEventHandler<TitusDeployPrepared> {

  private final TitusClient titusClient;
  private final RetrySupport retrySupport;

  public SubmitJobStep(TitusClient titusClient, RetrySupport retrySupport) {
    this.titusClient = titusClient;
    this.retrySupport = retrySupport;
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

  /**
   * NOTE: The single-element array usage is to get around line-for-line Groovy conversion variable
   * references inside of the lambda. This should really be refactored so that pattern isn't
   * necessary. It's really gross as-is.
   */
  @NotNull
  @Override
  public List<SagaEvent> apply(@NotNull TitusDeployPrepared event, @NotNull Saga saga) {
    final SubmitJobRequest submitJobRequest = event.getSubmitJobRequest();
    final TitusDeployDescription description = event.getDescription();
    final String[] nextServerGroupName = {event.getNextServerGroupName()};

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
                      // TODO(rz): I feel like this is really bad to do...?
                      // Sweep this under the rug...
                    }
                    retryCount[0]++;
                  }
                  nextServerGroupName[0] =
                      TitusJobNameResolver.resolveJobName(
                          titusClient, description, submitJobRequest);

                  saga.log("Resolved server group name to '%s'", nextServerGroupName[0]);

                  saga.log(
                      "Retrying with %s after %s attempts", nextServerGroupName[0], retryCount[0]);
                  throw e;
                }
                if (isStatusCodeRetryable(e.getStatus().getCode())) {
                  retryCount[0]++;
                  saga.log("Retrying after %s attempts", retryCount[0]);
                  throw e;
                } else {
                  log.error(
                      "Could not submit job and not retrying for status {}", e.getStatus(), e);
                  saga.log("Could not submit job %s: %s", e.getStatus(), e.getMessage());
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

    saga.log("Successfully submitted job request to Titus (Job URI: %s)", jobUri);

    return Collections.singletonList(
        new TitusJobSubmitted(
            saga.getName(),
            saga.getId(),
            description,
            event.getFront50App(),
            nextServerGroupName[0],
            Collections.singletonMap(description.getRegion(), nextServerGroupName[0]),
            jobUri,
            event.getTargetGroupLookupResult()));
  }

  @Override
  public void compensate(@NotNull TitusDeployPrepared event, @NotNull Saga saga) {}

  @Override
  public void finalize(@NotNull TitusDeployPrepared event, @NotNull Saga saga) {}
}
