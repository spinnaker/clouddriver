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

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public class SagaResult<T> {

  @Nullable private T result;

  @Nullable private Exception error;

  private boolean retryable;

  @Nonnull private List<Object> events;

  public SagaResult(@Nullable T result) {
    this.result = result;
  }

  public SagaResult(@Nullable Exception error, boolean retryable) {
    this.error = error;
    this.retryable = retryable;
  }

  public SagaResult(
      @Nullable T result,
      @Nullable Exception error,
      boolean retryable,
      @Nonnull List<Object> events) {
    this.result = result;
    this.error = error;
    this.retryable = retryable;
    this.events = events;
  }

  public boolean hasError() {
    return error != null;
  }
}
