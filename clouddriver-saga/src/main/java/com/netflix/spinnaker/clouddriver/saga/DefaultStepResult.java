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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DefaultStepResult implements StepResult {

  private final Map<String, Object> result = new HashMap<>();
  private final Exception error;

  public DefaultStepResult() {
    this.error = null;
  }

  public DefaultStepResult(@Nonnull Map<String, Object> result) {
    this(result, null);
  }

  public DefaultStepResult(Exception error) {
    this.error = error;
  }

  public DefaultStepResult(@Nonnull Map<String, Object> result, Exception error) {
    this.result.putAll(result);
    this.error = error;
  }

  @Nonnull
  @Override
  public Map<String, Object> getResults() {
    return result;
  }

  @Nullable
  @Override
  public Exception getError() {
    return error;
  }
}
