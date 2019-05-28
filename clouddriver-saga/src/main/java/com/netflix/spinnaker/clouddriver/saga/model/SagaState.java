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

import com.google.common.annotations.Beta;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.utils.Pair;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The state for an operation at a given version. */
@Beta
public interface SagaState extends Comparable {
  @Nonnull
  Instant getVersion();

  @Nonnull
  SagaStatus getStatus();

  void setStatus(@Nonnull SagaStatus status);

  @Nullable
  Error getError();

  void setError(@Nonnull Error error);

  @Nonnull
  Map<String, Object> getPersistedState();

  @Nullable
  <T> T get(@Nonnull String key);

  @Nonnull
  <T> T getRequired(@Nonnull String key);

  @Nullable
  <T> T get(@Nonnull String key, @Nonnull Class<T> stateType);

  @Nonnull
  <T> T getRequired(@Nonnull String key, @Nonnull Class<T> stateType);

  /**
   * Put a runtime-only object into the SagaState.
   *
   * <p>Any values added to the SagaState through this method must be re-populated on subsequent
   * runs of a SagaExecution. If you need to add persistent state, it must be output though a
   * StepResult.
   *
   * <p>TODO(rz): This should just get removed. Force runtime state to be injected into steps some
   * other way.
   *
   * @param key The name of the object
   * @param object The object to store
   */
  void put(@Nonnull String key, @Nullable Object object);

  void appendLog(@Nonnull String message);

  default void appendLog(@Nonnull String message, @Nonnull Object... arguments) {
    appendLog(String.format(message, arguments));
  }

  List<String> getLogs();

  /**
   * Creates a new SagaState with a new persisted state by merging the data of a StepResult atop the
   * existing persisted state. This method does not perform a commit action.
   *
   * <p>Error information will not be carried forward.
   *
   * @param stepResult The data to merge into a new SagaState object
   * @return The pair of SagaStates, left being the new state and right the prior state
   */
  @Nonnull
  Pair<SagaState, SagaState> merge(@Nullable StepResult stepResult);

  @Nonnull
  SagaState copy(@Nonnull Consumer<SagaState> customizer);

  @Override
  default int compareTo(@Nonnull Object o) {
    if (o instanceof SagaState) {
      return getVersion().compareTo(((SagaState) o).getVersion());
    }
    return 0;
  }

  interface Error {
    @Nonnull
    Exception getCause();

    @Nonnull
    String getUserMessage();

    @Nonnull
    String getOperatorMessage();
  }
}
