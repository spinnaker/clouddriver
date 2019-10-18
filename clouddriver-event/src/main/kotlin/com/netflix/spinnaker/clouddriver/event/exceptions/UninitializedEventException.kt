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
package com.netflix.spinnaker.clouddriver.event.exceptions

import com.netflix.spinnaker.kork.exceptions.IntegrationException

/**
 * Thrown when an event's metadata is attempted to be retrieved before it has been initialized
 * by the library.
 */
class UninitializedEventException : IntegrationException(
  "Cannot access event metadata before initialization"
), EventingException {
  init {
    retryable = false
  }
}
