/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.it.requests;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public abstract class BaseRequest<R> {

  private static final int SLEEP_STEP_SECONDS = 5;
  @JsonIgnore String baseUrl;

  public BaseRequest(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public abstract R executeAndValidate() throws Exception;

  public static void repeatUntilTrue(
      BooleanSupplier func, long duration, TimeUnit unit, String errorMsg)
      throws InterruptedException {
    long durationSeconds = unit.toSeconds(duration);
    for (int i = 0; i < (durationSeconds / SLEEP_STEP_SECONDS); i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(SLEEP_STEP_SECONDS));
      } else {
        return;
      }
    }
    fail(errorMsg);
  }
}
