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
package com.netflix.spinnaker.clouddriver.saga.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.saga.utils.Checksum;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

/**
 * Note to self: Moved normal Saga to SagaExecution. This class is meant for actual storage and
 * should have references to the various states and whatever else will need to be persisted.
 * Ideally, this class wouldn't change much and would instead push off most changes to the SagaState
 * class.
 *
 * <p>This should then be the entry class for the entire module, and also be the root object for
 * SagaRepository.
 */
@Builder
@Data
public class Saga {
  @Nonnull private final String id;
  @Nonnull private final Map<String, Object> inputs;
  @Nonnull private final String checksum;
  @Nonnull private final List<SagaStep> steps;
  @Nonnull private final Instant createdAt;
  @Nonnull private Instant updatedAt;
  @Nonnull private SagaDirection direction;
  @Nonnull private String owner;
  @Nonnull private SagaStatus status;

  @VisibleForTesting
  public static Saga createForTest(
      @Nonnull String id, @Nonnull Map<String, Object> inputs, @Nonnull List<SagaStep> steps) {
    Instant now = Instant.now();
    return Saga.builder()
        .id(id)
        .inputs(inputs)
        .checksum(Checksum.md5(inputs))
        .steps(steps)
        .createdAt(now)
        .updatedAt(now)
        .owner("testing")
        .direction(SagaDirection.FORWARD)
        .status(SagaStatus.RUNNING)
        .build();
  }

  /**
   * Finds the latest state, according to its version. If no states exist in the Saga yet, a default
   * state will be returned seeded by the inputs.
   */
  @JsonIgnore
  @SuppressWarnings("unchecked")
  @Nonnull
  public SagaState getLatestState() {
    return steps.stream()
        .flatMap(s -> s.getStates().stream())
        .min(Comparable::compareTo)
        .orElse(new DefaultSagaState(inputs));
  }

  @JsonIgnore
  @Nonnull
  public SagaStep getNextStep() {
    throw new UnsupportedOperationException("Saga restarts are not available yet");
  }

  public void restart() {
    SagaStep latestStep = getNextStep();
    latestStep.restart(getLatestState());
    status = SagaStatus.RUNNING;
  }

  @Nullable
  public SagaStep getStep(@Nonnull String id) {
    return steps.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
  }
}
