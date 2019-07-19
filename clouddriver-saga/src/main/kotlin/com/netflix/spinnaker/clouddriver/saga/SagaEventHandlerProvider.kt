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
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.ResolvableType

/**
 * Provides a list of [SagaEventHandler] that are capable of handling the provided [SagaEvent] and [Saga].
 *
 * This provider is [Saga]-aware so that [SagaEventHandler]s can statically require n+1 [SagaEvent] having
 * occurred prior to matching, allowing for join workflows and potential tooling.
 */
class SagaEventHandlerProvider : ApplicationContextAware {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private lateinit var applicationContext: ApplicationContext

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    this.applicationContext = applicationContext
  }

  fun getMatching(saga: Saga, event: SagaEvent): List<SagaEventHandler<SagaEvent>> {
    val eventType = event.javaClass
    val eventTypeName = eventType.simpleName

    @Suppress("UNCHECKED_CAST")
    val matchingHandlers = applicationContext.getBeansOfType(SagaEventHandler::class.java)
      .values
      .filter { handlerMatchesEvent(it, event, saga.getEvents()) } as List<SagaEventHandler<*>>

    if (matchingHandlers.isEmpty()) {
      log.trace("Could not resolve EventHandler for event: $eventTypeName")
      return listOf()
    }

    matchingHandlers.joinToString(",") { it.javaClass.simpleName }.also {
      log.trace("Resolved ${matchingHandlers.size} EventHandlers ($it) for event: $eventTypeName")
    }

    @Suppress("UNCHECKED_CAST")
    return matchingHandlers as List<SagaEventHandler<SagaEvent>>
  }

  private fun handlerMatchesEvent(handler: SagaEventHandler<*>, event: SagaEvent, events: List<SagaEvent>): Boolean {
    val handlerType = ResolvableType.forClass(SagaEventHandler::class.java, handler.javaClass)
    handlerType.resolve()

    val genericEventType = handlerType.getGeneric(0)
    genericEventType.resolve()

    if (genericEventType.rawClass == null) {
      log.error("No generic event type, this should never happen: " +
        "${handler.javaClass.simpleName}, ${event.javaClass.simpleName}")
      return false
    }

    return matchTypes(genericEventType, event::class.java, events)
  }

  private fun matchTypes(
    genericEventType: ResolvableType,
    eventType: Class<out SagaEvent>,
    appliedEvents: List<SagaEvent>
  ): Boolean {
    if (!CompositeSagaEvent::class.java.isAssignableFrom(genericEventType.rawClass)) {
      return genericEventType.isAssignableFrom(eventType)
    }

    if (UnionedSagaEvent::class.java.isAssignableFrom(genericEventType.rawClass)) {
      return allRequiredEventsExist(
        genericEventType,
        appliedEvents.map { it.javaClass.simpleName }.plus(eventType.simpleName)
      )
    }

    val nestedCompositeEvents = genericEventType
      .generics
      .filter { CompositeSagaEvent::class.java.isAssignableFrom(it.rawClass) }
    if (nestedCompositeEvents.isEmpty()) {
      return genericEventType.generics.any { it.isAssignableFrom(eventType) }
    }

    return nestedCompositeEvents.all { matchTypes(it, eventType, appliedEvents) }
  }

  private fun allRequiredEventsExist(unionEventType: ResolvableType, seenEventNames: List<String>): Boolean =
    seenEventNames.containsAll(unionEventType.generics.mapNotNull { it.rawClass?.simpleName })
}
