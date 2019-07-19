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
 *
 */
package com.netflix.spinnaker.clouddriver.saga

import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

class SagaEventFactoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {

    fixture {
      Fixture()
    }

    test("builds a primitive event") {
      val applyingEvent = EventA(saga)
      val handler = PrimitiveHandler()

      expectThat(subject.buildCompositeEventForHandler(saga, handler, applyingEvent)) {
        isA<EventA>()
      }
    }

    test("builds a union event") {
      val eventA = EventA(saga)
      val eventB = EventB(saga)
      val handler = UnionHandler()

      every { eventRepository.list(any(), any()) } returns listOf(eventB)

      expectThat(subject.buildCompositeEventForHandler(saga, handler, eventA)) {
        isA<UnionSagaEvent2<EventA, EventB>>().and {
          get { a }.isEqualTo(eventA)
          get { b }.isEqualTo(eventB)
        }
      }
    }

    test("builds an either event") {
      val eventA = EventA(saga)
      val eventB = EventB(saga)
      val handler = EitherHandler()

      every { eventRepository.list(any(), any()) } returns listOf(eventB)

      expectThat(subject.buildCompositeEventForHandler(saga, handler, eventA)) {
        isA<EitherSagaEvent2<EventA, EventB>>().and {
          get { a }.isEqualTo(eventA)
          get { b }.isEqualTo(eventB)
        }
      }
    }

    test("builds a nested event") {
      val eventA = EventA(saga)
      val eventB = EventB(saga)
      val eventC = EventC(saga)
      val handler = NestedHandler()

      every { eventRepository.list(any(), any()) } returns listOf(eventB, eventC)

      expectThat(subject.buildCompositeEventForHandler(saga, handler, eventA)) {
        isA<EitherSagaEvent2<EventA, UnionSagaEvent2<EventB, EventC>>>().and {
          get { a }.isEqualTo(eventA)
          get { b }.isNotNull().and {
            get { a }.isEqualTo(eventB)
            get { b }.isEqualTo(eventC)
          }
        }
      }
    }

    test("selects the most recent event if more than one of the same event exists") {
      val applyingEvent = EventA(saga)
      val handler = UnionHandler()

      val old = EventB(saga)
      val new = EventB(saga)
      every { eventRepository.list(any(), any()) } returns listOf(old, new)

      expectThat(subject.buildCompositeEventForHandler(saga, handler, applyingEvent)) {
        isA<UnionSagaEvent2<EventA, EventB>>().and {
          get { b }.isEqualTo(new)
        }
      }
    }
  }

  private inner class Fixture {
    val eventRepository: EventRepository = mockk()
    val subject = SagaEventFactory(eventRepository)

    val saga = Saga(
      name = "name",
      id = "id",
      completionHandler = null,
      requiredEvents = listOf(),
      compensationEvents = listOf()
    )
  }

  private inner class EventA(saga: Saga) : SagaEvent(saga.name, saga.id)
  private inner class EventB(saga: Saga) : SagaEvent(saga.name, saga.id)
  private inner class EventC(saga: Saga) : SagaEvent(saga.name, saga.id)

  private inner class PrimitiveHandler : SagaEventHandler<EventA> {
    override fun apply(event: EventA, saga: Saga): List<SagaEvent> {
      throw UnsupportedOperationException("not implemented")
    }
  }
  private inner class UnionHandler : SagaEventHandler<UnionSagaEvent2<EventA, EventB>> {
    override fun apply(event: UnionSagaEvent2<EventA, EventB>, saga: Saga): List<SagaEvent> {
      throw UnsupportedOperationException("not implemented")
    }
  }
  private inner class EitherHandler : SagaEventHandler<EitherSagaEvent2<EventA, EventB>> {
    override fun apply(event: EitherSagaEvent2<EventA, EventB>, saga: Saga): List<SagaEvent> {
      throw UnsupportedOperationException("not implemented")
    }
  }
  private inner class NestedHandler : SagaEventHandler<EitherSagaEvent2<EventA, UnionSagaEvent2<EventB, EventC>>> {
    override fun apply(event: EitherSagaEvent2<EventA, UnionSagaEvent2<EventB, EventC>>, saga: Saga): List<SagaEvent> {
      throw UnsupportedOperationException("not implemented")
    }
  }
}
