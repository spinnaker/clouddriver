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
package com.netflix.spinnaker.clouddriver.util

import com.netflix.spinnaker.kork.exceptions.SystemException

class Either<A, B>(
  val a: A?,
  val b: B?
) {
  init {
    if (a == null && b == null) {
      throw IllegalArgumentsException()
    }
    if (a != null && b != null) {
      throw IllegalArgumentsException()
    }
  }

  private class IllegalArgumentsException : SystemException("Only one type of Either<A, B> must be set")
}
