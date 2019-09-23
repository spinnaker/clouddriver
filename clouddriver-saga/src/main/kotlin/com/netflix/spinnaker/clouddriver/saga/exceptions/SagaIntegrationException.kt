/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.saga.exceptions

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SpinnakerException

/**
 * Thrown when code using the Saga framework has generated an uncaught exception, it will be wrapped by this
 * Exception and re-thrown.
 */
open class SagaIntegrationException(message: String, cause: Throwable?) :
  IntegrationException(message, cause), SagaException {

  constructor(message: String) : this(message, null)

  init {
    // Defer to the cause for retryable; but default to retryable if the retryable flag is unavailable.
    retryable = if (cause is SpinnakerException) {
      cause.retryable ?: true
    } else {
      true
    }
  }
}
