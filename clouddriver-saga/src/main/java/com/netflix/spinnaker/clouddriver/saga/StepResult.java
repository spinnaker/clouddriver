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

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Result wrapper for SagaSteps.
 *
 * <p>TODO(rz): Wrap error in its own object to include more metadata than an exception?
 *
 * <p>TODO(rz): Add optional list of events that a step can emit to the rest of the system.
 */
public interface StepResult {
  @Nonnull
  Map<String, Object> getResults();

  /** TODO(rz): We need more error metadata here. */
  @Nullable
  Exception getError();

  default boolean isRetryable() {
    return true;
  }
}
