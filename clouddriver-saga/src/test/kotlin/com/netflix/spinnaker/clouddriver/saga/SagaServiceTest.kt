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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.event.EventPublisher
import com.netflix.spinnaker.clouddriver.event.SynchronousEventPublisher
import com.netflix.spinnaker.clouddriver.event.config.MemoryEventRepositoryConfigProperties
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.event.persistence.MemoryEventRepository
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.DefaultSagaRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThrows

class SagaServiceTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {

    fixture {
      Fixture()
    }

    context("applying a non-existent saga") {
      test("throws system exception") {
        val saga = Saga(
          name = "noexist",
          id = "nope",
          requiredEvents = listOf(),
          compensationEvents = listOf()
        )
        expectThrows<SagaSystemException> {
          subject.apply(EmptyEvent(saga))
        }
      }
    }

    context("applying an event to a saga") {
      test("no matching event handler does nothing") {
        val saga = Saga(
          name = "test",
          id = "1",
          requiredEvents = listOf(),
          compensationEvents = listOf()
        )

        every { handlerProvider.getMatching(any(), any()) } returns listOf()

        subject.save(saga)
        subject.apply(EmptyEvent(saga).apply {
          metadata = EventMetadata(sequence = 0L, originatingVersion = 0L)
        })
      }

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

  inner class Fixture {
    val eventPublisher: EventPublisher = SynchronousEventPublisher()

    val eventRepository: EventRepository = MemoryEventRepository(
      MemoryEventRepositoryConfigProperties(),
      eventPublisher,
      NoopRegistry()
    )

    val handlerProvider: SagaEventHandlerProvider = mockk(relaxed = true)

    val subject: SagaService = SagaService(
      DefaultSagaRepository(eventRepository),
      eventRepository,
      handlerProvider,
      eventPublisher,
      NoopRegistry()
    )
  }

  inner class EmptyEvent(saga: Saga) : SagaEvent(saga.name, saga.id)
}
