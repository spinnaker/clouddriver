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
package com.netflix.spinnaker.clouddriver.saga.persistence

import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.saga.SagaSaved
import com.netflix.spinnaker.clouddriver.saga.models.Saga

class MemorySagaRepository(
  private val eventRepository: EventRepository
) : SagaRepository {

  override fun get(type: String, id: String): Saga? {
    val events = eventRepository.list(type, id)
    if (events.isEmpty()) {
      return null
    }

    return events
      .filterIsInstance<SagaSaved>()
      .last()
      .saga
      .let {
        // Copy the Saga: We don't want to accidentally mutate the saga that's in the event if the eventRepository is in-memory only.
        Saga(
          name = it.name,
          id = it.id,
          requiredEvents = it.getRequiredEvents(),
          compensationEvents = it.compensationEvents
        )
      }
      .apply { hydrateEvents(events) }
  }

  override fun save(saga: Saga) {
    eventRepository.save(saga.name, saga.id, saga.getVersion(), listOf(
      SagaSaved(saga)
    ))
  }
}
