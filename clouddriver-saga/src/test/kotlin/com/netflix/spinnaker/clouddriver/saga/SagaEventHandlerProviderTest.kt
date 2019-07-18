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

import com.netflix.spinnaker.clouddriver.saga.models.Saga
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty

class SagaEventHandlerProviderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {

    fixture {
      Fixture()
    }

    context("matching an event to handlers") {
      test("an event without a matching handler") {
        val event = AnotherEvent(saga.name, saga.id)
        expectThat(subject.getMatching(saga, event)).isEmpty()
      }

      test("an event with one matching handler") {
        val event = EmptyEvent(saga.name, saga.id)
        expectThat(subject.getMatching(saga, event)) {
          containsExactly(emptyHandler)
        }
      }

      test("an event with multiple matching handlers") {
        val event = HelloEvent(saga.name, saga.id)
        expectThat(subject.getMatching(saga, event)) {
          containsExactlyInAnyOrder(helloHandler, eitherHandler)
        }
      }

      test("an event with a matching union handler") {
        saga.addEventForTest(GoodbyeEvent(saga.name, saga.id))

        val event = HelloEvent(saga.name, saga.id)
        expectThat(subject.getMatching(saga, event)) {
          containsExactlyInAnyOrder(helloHandler, unionHandler, eitherHandler, megaHandler)
        }
      }

      test("events with a matching either handler") {
        expectThat(subject.getMatching(saga, HelloEvent(saga.name, saga.id))) {
          contains(eitherHandler)
        }

        expectThat(subject.getMatching(saga, GoodbyeEvent(saga.name, saga.id))) {
          contains(eitherHandler)
        }
      }
    }
  }

  inner class Fixture {
    var applicationContext: ApplicationContext = mockk()
    var saga = Saga(
      name = "deploy",
      id = "1",
      completionHandler = "completionHandler",
      requiredEvents = listOf(EmptyEvent::class.java.simpleName, HelloEvent::class.java.simpleName),
      compensationEvents = listOf()
    )
    var subject = SagaEventHandlerProvider().apply {
      setApplicationContext(applicationContext)
    }

    val emptyHandler = EmptySagaEventHandler()
    val helloHandler = HelloSagaEventHandler()
    val unionHandler = UnionSagaEventHandler()
    val eitherHandler = EitherSagaEventHandler()
    val megaHandler = MegaCompositeSagaEventHandler()

    init {
      every { applicationContext.getBeansOfType(SagaEventHandler::class.java) } returns mapOf(
        "empty" to emptyHandler,
        "hello" to helloHandler,
        "union" to unionHandler,
        "either" to eitherHandler,
        "mega" to megaHandler
      )
    }
  }

  inner class EmptyEvent(sagaName: String, sagaId: String) : SagaEvent(sagaName, sagaId)
  inner class HelloEvent(sagaName: String, sagaId: String) : SagaEvent(sagaName, sagaId)
  inner class AnotherEvent(sagaName: String, sagaId: String) : SagaEvent(sagaName, sagaId)
  inner class GoodbyeEvent(sagaName: String, sagaId: String) : SagaEvent(sagaName, sagaId)

  inner class EmptySagaEventHandler : SagaEventHandler<EmptyEvent> {
    override fun apply(event: EmptyEvent, saga: Saga): List<SagaEvent> = listOf()
  }
  inner class HelloSagaEventHandler : SagaEventHandler<HelloEvent> {
    override fun apply(event: HelloEvent, saga: Saga): List<SagaEvent> = listOf()
  }
  inner class UnionSagaEventHandler : SagaEventHandler<UnionSagaEvent2<HelloEvent, GoodbyeEvent>> {
    override fun apply(event: UnionSagaEvent2<HelloEvent, GoodbyeEvent>, saga: Saga): List<SagaEvent> = listOf()
  }
  inner class EitherSagaEventHandler : SagaEventHandler<EitherSagaEvent2<HelloEvent, GoodbyeEvent>> {
    override fun apply(event: EitherSagaEvent2<HelloEvent, GoodbyeEvent>, saga: Saga): List<SagaEvent> = listOf()
  }
  inner class MegaCompositeSagaEventHandler : SagaEventHandler<EitherSagaEvent2<AnotherEvent, UnionSagaEvent2<HelloEvent, GoodbyeEvent>>> {
    override fun apply(event: EitherSagaEvent2<AnotherEvent, UnionSagaEvent2<HelloEvent, GoodbyeEvent>>, saga: Saga): List<SagaEvent> = listOf()
  }
}
