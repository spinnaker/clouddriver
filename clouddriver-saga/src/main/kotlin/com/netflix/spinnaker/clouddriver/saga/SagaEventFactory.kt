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
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import org.slf4j.LoggerFactory
import org.springframework.core.ResolvableType

// TODO(rz): Remove eventRepository in favor of [saga.getEvents()]
class SagaEventFactory(
  private val eventRepository: EventRepository
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun buildCompositeEventForHandler(
    saga: Saga,
    handler: SagaEventHandler<*>,
    applyingEvent: SagaEvent
  ): SagaEvent {
    log.debug("Building event for handler '${handler.javaClass.simpleName}' from '${applyingEvent.javaClass.simpleName}")

    val handlerType = ResolvableType.forClass(SagaEventHandler::class.java, handler.javaClass)
    handlerType.resolve()

    val eventType = handlerType.getGeneric(0)
    eventType.resolve()

    if (!eventType.hasGenerics()) {
      return applyingEvent
    }

    // Recursively resolve all event types; then order by deepest first
    val rootContainer = EventTypeContainer(null, eventType)
    val reversedTypes = mutableListOf(rootContainer)
      .plus(
        eventType.generics
          .map { deeplyResolveTypes(EventTypeContainer(rootContainer, it)) }
          .flatten()
      )
      .reversed()

    // Resolve all of the actual event values for each type
    eventRepository.list(saga.name, saga.id).plus(applyingEvent)
      .filterIsInstance<SagaEvent>()
      .also { events ->
        reversedTypes.forEach { resolveEvent(it, events) }
      }

    // Reconstruct the event from the bottom-up
    reversedTypes.forEach {
      if (it.parent != null) {
        if (it.value != null) {
          it.parent.childValues.add(it.value!!)
        } else {
          it.parent.childValues.add(it.toEvent(saga))
        }
      }
    }

    // Find the root event container and convert it to the proper event
    return reversedTypes
      .find { it.parent == null }
      ?.toEvent(saga)
      ?: throw SagaSystemException("Could not find root event")
  }

  private fun deeplyResolveTypes(container: EventTypeContainer): List<EventTypeContainer> {
    if (!container.type.hasGenerics()) {
      return listOf(container)
    }

    val childTypes = container.type.generics.map { deeplyResolveTypes(EventTypeContainer(container, it)) }.flatten()
    return listOf(container).plus(childTypes)
  }

  private fun resolveEvent(container: EventTypeContainer, events: List<SagaEvent>) {
    if (CompositeSagaEvent::class.java.isAssignableFrom(container.type.rawClass)) {
      // Composites don't have an actual event value
      return
    }

    val event = events
      .asReversed()
      .firstOrNull { it.javaClass.isAssignableFrom(container.type.rawClass) }
      ?: throw SagaSystemException("Unable to find event value for ${container.type.rawClass?.simpleName ?: UNKNOWN}")
    container.value = event
  }

  private inner class EventTypeContainer(
    val parent: EventTypeContainer?,
    val type: ResolvableType
  ) {
    var value: SagaEvent? = null

    val childValues: MutableList<SagaEvent> = mutableListOf()

    fun toEvent(saga: Saga): SagaEvent {
      if (UnionedSagaEvent::class.java.isAssignableFrom(type.rawClass)) {
        if (type.generics.size != childValues.size) {
          throw SagaSystemException("Mismatch of expected child values and actual")
        }
        childValues.reverse()
        return when (type.generics.size) {
          2 -> UnionSagaEvent2(saga, childValues[0], childValues[1])
          3 -> UnionSagaEvent3(saga, childValues[0], childValues[1], childValues[2])
          4 -> UnionSagaEvent4(saga, childValues[0], childValues[1], childValues[2], childValues[3])
          5 -> UnionSagaEvent5(saga, childValues[0], childValues[1], childValues[2], childValues[3], childValues[4])
          else -> throw SagaSystemException("could not map values onto ${type.rawClass?.simpleName ?: UNKNOWN}")
        }
      }
      if (EitherSagaEvent::class.java.isAssignableFrom(type.rawClass)) {
        if (type.generics.size != childValues.size) {
          throw SagaSystemException("Mismatch of expected child values and actual")
        }
        childValues.reverse()
        return when (type.generics.size) {
          2 -> EitherSagaEvent2(saga, childValues[0], childValues[1])
          3 -> EitherSagaEvent3(saga, childValues[0], childValues[1], childValues[2])
          4 -> EitherSagaEvent4(saga, childValues[0], childValues[1], childValues[2], childValues[3])
          5 -> EitherSagaEvent5(saga, childValues[0], childValues[1], childValues[2], childValues[3], childValues[4])
          else -> throw SagaSystemException("could not map values onto ${type.rawClass?.simpleName ?: UNKNOWN}")
        }
      }
      return value ?: throw SagaSystemException("null value for ${type.rawClass?.simpleName ?: UNKNOWN}")
    }
  }

  companion object {
    private const val UNKNOWN = "UNKNOWN_TYPE"
  }
}
