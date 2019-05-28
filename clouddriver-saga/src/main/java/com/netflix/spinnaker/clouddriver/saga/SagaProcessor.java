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

import com.google.common.annotations.Beta;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import java.util.function.Function;
import javax.annotation.Nonnull;

/** Processes Saga objects. */
@Beta
public interface SagaProcessor {

  /** Process the provided [saga], resuming from it's left off state if needed. */
  @Nonnull
  <T> SagaResult<T> process(Saga saga, Function<SagaState, T> resultCallback);
}
