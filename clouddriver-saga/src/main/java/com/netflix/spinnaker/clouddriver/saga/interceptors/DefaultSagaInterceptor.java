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
package com.netflix.spinnaker.clouddriver.saga.interceptors;

import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import javax.annotation.Nonnull;

/** Provides core orchestration functionality for Sagas. */
public class DefaultSagaInterceptor implements SagaInterceptor {

  /** TODO(rz): This should be configurable. */
  private static final int MAX_STEP_ATTEMPTS = 3;

  @Override
  public boolean shouldSkipStep(@Nonnull SagaStep sagaStep) {
    return sagaStep.getStatus() == SagaStatus.SUCCEEDED;
  }

  @Override
  public boolean shouldFailSaga(@Nonnull Saga saga, @Nonnull SagaStep sagaStep) {
    if (saga.getLatestState().getStatus() == SagaStatus.TERMINAL_FATAL) {
      return true;
    }
    return sagaStep.getAttempt() > MAX_STEP_ATTEMPTS;
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
