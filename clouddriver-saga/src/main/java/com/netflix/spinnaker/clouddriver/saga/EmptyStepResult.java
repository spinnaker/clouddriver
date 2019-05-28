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

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An empty result. */
public class EmptyStepResult implements StepResult {

  private static final EmptyStepResult INSTANCE = new EmptyStepResult();

  private EmptyStepResult() {}

  public static EmptyStepResult getInstance() {
    return INSTANCE;
  }

  @Nonnull
  @Override
  public Map<String, Object> getResults() {
    return Collections.emptyMap();
  }

  @Nullable
  @Override
  public Exception getError() {
    return null;
  }
}
