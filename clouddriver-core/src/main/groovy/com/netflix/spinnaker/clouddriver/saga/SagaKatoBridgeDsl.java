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

import com.google.common.annotations.Beta;
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaDirection;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import com.netflix.spinnaker.clouddriver.saga.utils.Checksum;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

/** A bridge DSL between the Saga format of structuring AtomicOperations and V1 Tasks. */
@Beta
@Slf4j
public class SagaKatoBridgeDsl {

  @Nonnull private final Task task;
  @Nonnull private final Saga.SagaBuilder sagaBuilder = Saga.builder();
  @Nonnull private final List<SagaStep.SagaStepBuilder> stepBuilders = new ArrayList<>();

  public SagaKatoBridgeDsl() {
    this.task = TaskRepository.threadLocalTask.get();
  }

  /** For testing purposes only */
  private SagaKatoBridgeDsl(Task task) {
    this.task = task;
  }

  public SagaKatoBridgeDsl inputs(@Nonnull Map<String, Object> inputs) {
    sagaBuilder.inputs(inputs);
    sagaBuilder.checksum(Checksum.md5(inputs));
    return this;
  }

  public SagaKatoBridgeDsl step(@Nonnull SagaStep.SagaStepBuilder sagaStepBuilder) {
    stepBuilders.add(sagaStepBuilder);
    return this;
  }

  public Saga build() {
    if (stepBuilders.isEmpty()) {
      throw new NoStepsDefinedException();
    }

    Instant now = Instant.now();

    String id = task.getRequestId();
    if (id == null) {
      log.warn(
          "Kato task ({}) does not have a request ID associated: Using kato task ID for Saga ID; this will make the Saga non-retryable",
          task.getId());
      id = task.getId();
    }

    Saga saga =
        sagaBuilder
            .id(id)
            .owner(task.getOwnerId())
            .status(SagaStatus.RUNNING)
            .direction(SagaDirection.FORWARD)
            .owner(ClouddriverHostname.ID)
            .steps(new ArrayList<>())
            .createdAt(now)
            .updatedAt(now)
            .build();

    saga.getSteps()
        .addAll(
            stepBuilders.stream()
                .map(
                    builder -> {
                      return builder.saga(saga).build();
                    })
                .collect(Collectors.toList()));

    return saga;
  }

  public static class StepBuilder {
    @Nonnull protected final String id;
    @Nonnull protected final String label;
    private Function<SagaState, StepResult> function;

    StepBuilder(@Nonnull String id, @Nonnull String label) {
      this.id = id;
      this.label = label;
    }

    public static StepBuilder newStep(@Nonnull String id, @Nonnull String label) {
      return new StepBuilder(id, label);
    }

    @Nonnull
    public StepBuilder fn(@Nonnull Function<SagaState, StepResult> fn) {
      this.function = fn;
      return this;
    }

    @Nonnull
    public SagaStep.SagaStepBuilder build() {
      if (function == null) {
        throw new MisconfiguredStepException(
            format("OperationStep '%s' (id: %s) does not have a function defined", label, id));
      }

      Instant now = Instant.now();

      return SagaStep.builder()
          .id(id)
          .label(label)
          .attempt(0)
          .fn(function)
          .states(new ArrayList<>())
          .createdAt(now)
          .updatedAt(now);
    }
  }

  private static class NoStepsDefinedException extends IntegrationException {
    NoStepsDefinedException() {
      super("At least one SagaStep must be defined");
    }
  }

  private static class MisconfiguredStepException extends IntegrationException {
    MisconfiguredStepException(String message) {
      super(message);
    }
  }
}
