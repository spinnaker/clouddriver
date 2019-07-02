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
package com.netflix.spinnaker.clouddriver.saga;

import static java.lang.String.format;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.saga.interceptors.SagaInterceptor;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * While it works, this class is pretty atrocious and could use some more love. Once the concept is
 * proven, this class will be refactored to become event-driven so recovery and process flows are a
 * little easier to reason about and extend.
 */
@Slf4j
public class DefaultSagaProcessor implements SagaProcessor {

  private final SagaRepository sagaRepository;
  private final Registry registry;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final List<SagaInterceptor> sagaInterceptors;

  public DefaultSagaProcessor(
      SagaRepository sagaRepository,
      Registry registry,
      ApplicationEventPublisher applicationEventPublisher,
      List<SagaInterceptor> sagaInterceptors) {
    this.sagaRepository = sagaRepository;
    this.registry = registry;
    this.applicationEventPublisher = applicationEventPublisher;
    this.sagaInterceptors = sagaInterceptors;
  }

  @Override
  @Nonnull
  public <T> SagaResult<T> process(Saga saga, Function<SagaState, T> callbackFunction) {
    log.info("Starting saga: {}", saga.getId());
    SagaResult<T> result = null;
    do {
      // TODO(rz): Add retry delay
      if (result != null) {
        log.info("Retrying saga: {}", saga.getId());
      }
      result = processInternal(saga, callbackFunction);
    } while (result.hasError() && result.isRetryable());

    log.info("Saga completed: {}", saga.getId());

    return result;
  }

  /**
   * This method is a disaster... but it also covers the basic functionality that needs to ship.
   *
   * <p>TODO(rz): Un-bad this.
   */
  @Nonnull
  private <T> SagaResult<T> processInternal(
      Saga inputSaga, Function<SagaState, T> callbackFunction) {
    Saga saga = initializeSaga(inputSaga);

    SagaState latestState = saga.getLatestState();
    for (SagaStep step : saga.getSteps()) {
      log.info("Starting step: {}:{} (attempt: {})", saga.getId(), step.getId(), step.getAttempt());

      ProcessStepResult<T> result = processStep(saga, step, latestState);

      sagaInterceptors.forEach(
          i -> {
            log.debug(
                "Invoking {} on {}:{}", i.getClass().getSimpleName(), saga.getId(), step.getId());
            i.afterProcessingStep(saga, step, result.result);
          });

      log.info("Finished step: {}:{}", saga.getId(), step.getId());

      if (result.result != null) {
        return result.result;
      }
      if (result.latestState != null) {
        latestState = result.latestState;
      }
    }

    try {
      T result = callbackFunction.apply(latestState);

      saga.setStatus(SagaStatus.SUCCEEDED);
      sagaRepository.upsert(saga);

      return new SagaResult<>(result);
    } catch (Exception e) {
      throw new IntegrationException(
          "OperationSagaProcessor callback function failed to produce a result", e);
    }
  }

  private <T> ProcessStepResult<T> processStep(Saga saga, SagaStep step, SagaState latestState) {
    // Allow any interceptors to apply skip logic to steps.
    if (sagaInterceptors.stream().anyMatch(i -> i.shouldSkipStep(step))) {
      log.info("Skipping step: {}:{}", saga.getId(), step.getId());
      return new ProcessStepResult<>();
    }

    // Before we try to apply the step (potentially again), allow the interceptors to permanently
    // fail the saga.
    if (sagaInterceptors.stream().anyMatch(i -> i.shouldFailSaga(saga, step))) {
      log.error("Failing saga '{}' due to step '{}'", saga.getId(), step.getId());
      saga.setStatus(SagaStatus.TERMINAL_FATAL);
      sagaRepository.upsert(saga);

      // TODO(rz): Should save some reason alongside (e.g. "failed after max attempts", etc)
      return new ProcessStepResult<>(
          new SagaResult<>(
              new FatalStepException("Saga failed to apply after max attempts"), false));
    }

    // Before any step gets processed (even on retry!), we need to copy the state and set its
    // status to RUNNING
    final SagaState stepState =
        step.getLatestState(latestState).copy(it -> it.setStatus(SagaStatus.RUNNING));
    step.getStates().add(stepState);

    // Apply the step
    final StepResult stepResult;
    try {
      stepResult = step.getFn().apply(stepState);
    } catch (Exception e) {
      log.error(
          "Error occurred while attempting to apply step {}:{}", saga.getId(), step.getId(), e);

      step.setAttempt(step.getAttempt() + 1);
      stepState.setStatus(SagaStatus.TERMINAL);

      sagaRepository.upsert(step);

      // TODO(rz): Make less bad
      return new ProcessStepResult<>(
          new SagaResult<>(new IntegrationException("Failed applying step", e), true), stepState);
    }

    // And merge results into a new state and save the final results.
    try {
      // TODO(rz): Haven't used getRight; probably should delete/simplify
      SagaState newState = stepState.merge(stepResult).getLeft();

      newState.setStatus(SagaStatus.SUCCEEDED);
      step.getStates().add(newState);

      sagaRepository.upsert(step);

      return new ProcessStepResult<>(newState);
    } catch (Exception e) {
      throw new SystemException("Failed updating operation state", e);
    }
  }

  private Saga initializeSaga(Saga saga) {
    Saga storedSaga = sagaRepository.get(saga.getId());
    if (storedSaga == null) {
      return sagaRepository.upsert(saga);
    }

    // Perform a checksum on the originally stored inputs versus the inputs provided from the most
    // recent execution. If these are not the same, we will abort this specific request.
    if (!saga.getChecksum().equals(storedSaga.getChecksum())) {
      throw new InputsChecksumMismatchException(saga.getChecksum(), storedSaga.getChecksum());
    }

    // If we're picking up a saga where the latest state does not have a RUNNING status set it to
    // RUNNING and increment the attempts.
    if (saga.getStatus() != SagaStatus.RUNNING) {
      saga.restart();
      return sagaRepository.upsert(saga);
    }

    return saga;
  }

  public static class InputsChecksumMismatchException extends UserException {
    InputsChecksumMismatchException(String storedChecksum, String providedChecksum) {
      super(
          format(
              "Provided inputs checksum (%s) does not match original stored checksum (%s)",
              storedChecksum, providedChecksum));
    }
  }

  public static class FatalStepException extends IntegrationException {
    public FatalStepException(String message) {
      super(message);
    }
  }

  private static class ProcessStepResult<T> {
    SagaResult<T> result;
    SagaState latestState;

    public ProcessStepResult() {}

    public ProcessStepResult(SagaResult<T> result) {
      this(result, null);
    }

    public ProcessStepResult(SagaState latestState) {
      this.latestState = latestState;
    }

    public ProcessStepResult(SagaResult<T> result, SagaState latestState) {
      this.result = result;
      this.latestState = latestState;
    }
  }
}
