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

/**
 * Thrown when a Saga's configured `completionHandler` property cannot be resolved to a Bean in the
 * ApplicationContext.
 */
class InvalidSagaCompletionHandlerException(
  message: String,
  cause: Throwable
) : IntegrationException(message, cause) {
  companion object {
    fun notFound(handlerName: String, sagaName: String, cause: Exception): InvalidSagaCompletionHandlerException =
      InvalidSagaCompletionHandlerException(
        "Could not locate completion handler '$handlerName' for '$sagaName'", cause)

    fun invalidType(
      handlerName: String,
      sagaName: String,
      typeName: String,
      cause: Exception
    ): InvalidSagaCompletionHandlerException =
      InvalidSagaCompletionHandlerException(
        "Completion handler '$handlerName' for '$sagaName' not of type SagaCompletionHandler: '$typeName' given",
        cause
      )
  }
}
