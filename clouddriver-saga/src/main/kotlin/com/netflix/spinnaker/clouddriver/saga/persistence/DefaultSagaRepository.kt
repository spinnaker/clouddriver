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
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaSaved
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import org.slf4j.LoggerFactory

class DefaultSagaRepository(
  private val eventRepository: EventRepository
) : SagaRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun list(criteria: SagaRepository.ListCriteria): List<Saga> = TODO()

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
          completionHandler = it.completionHandler,
          requiredEvents = it.getRequiredEvents(),
          compensationEvents = it.compensationEvents,
          sequence = it.getSequence()
        )
      }
      .also { saga ->
        saga.hydrateEvents(events.filterIsInstance<SagaEvent>())
      }
  }

  override fun save(saga: Saga, additionalEvents: List<SagaEvent>?) {
    // TODO(rz): The saga should just record internal events that have occurred (SagaSequenceUpdated, SagaCompleted, etc) then save those
    val events: MutableList<SagaEvent> = mutableListOf(SagaSaved(saga))
    events.addAll(saga.getPendingEvents())
    if (additionalEvents != null) {
      events.addAll(additionalEvents)
    }
    eventRepository.save(saga.name, saga.id, saga.getVersion(), events)
  }
}
