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
package com.netflix.spinnaker.clouddriver.saga

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext

class SagaServiceTest : JUnit5Minutests {

  fun tests() = rootContext {

    context("applying a non-existent saga") {
      test("throws an illegal state exception") {}
    }

    context("applying an event to a saga") {
      test("no matching event handler does nothing") {}
      test("a matching event handler applies the event") {}
      test("a duplicate event is ignored") {}
      test("an event applied out-of-order is ignored") {}
      test("a required event is applied") {}
      test("an non-required event is applied") {}
      test("errors cause a general compensation event to be emitted") {}
    }

    context("finishing a saga") {
      test("calls the finalize method of a handler") {}
    }

    context("compensating for an error") {
      test("calls the compensate method of a handler") {}
    }
  }
}
