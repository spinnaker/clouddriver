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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Builder
@Data
@ToString(exclude = "saga")
public class SagaStep {

  @Nonnull private String id;
  @JsonBackReference @Nonnull private Saga saga;
  @Nonnull private String label;
  @Nonnull private Instant createdAt;
  @Nonnull private Instant updatedAt;
  @Nonnull private List<SagaState> states;
  @Nullable private Object output;
  @JsonIgnore @Nonnull private Function<SagaState, StepResult> fn;
  private int attempt;

  @SuppressWarnings("unchecked")
  public SagaStatus getStatus() {
    return states.stream()
        .min(Comparable::compareTo)
        .map(SagaState::getStatus)
        .orElse(SagaStatus.NOT_STARTED);
  }

  @SuppressWarnings("unchecked")
  public void restart(@Nonnull SagaState fallbackState) {
    attempt++;

    SagaState state =
        states.stream().min(Comparable::compareTo).orElse(fallbackState).merge(null).getLeft();
    state.setStatus(SagaStatus.RUNNING);

    states.add(state);
  }

  @SuppressWarnings("unchecked")
  @JsonIgnore
  @Nonnull
  public SagaState getLatestState(@Nonnull SagaState orElseState) {
    return states.stream().min(Comparable::compareTo).orElse(orElseState.merge(null).getLeft());
  }
}
