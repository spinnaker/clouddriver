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

  /**
   * TODO(rz): I'm doing naughty things in here...
   */
  fun getMatching(saga: Saga, event: SagaEvent): List<SagaEventHandler<SagaEvent>> {
    val eventType = event.javaClass
    val eventTypeName = eventType.simpleName

    @Suppress("UNCHECKED_CAST")
    val matchingHandlers = applicationContext.getBeansOfType(SagaEventHandler::class.java)
      .filter { handlerMatchesEvent(it.value, event, saga.getEvents()) } as List<SagaEventHandler<*>>

    if (matchingHandlers.isEmpty()) {
      log.error("Could not resolve EventHandler for event: $eventTypeName")
      return listOf()
    }

    matchingHandlers.joinToString(",") { it.javaClass.simpleName }.also {
      log.debug("Resolved ${matchingHandlers.size} EventHandlers ($it) for event: $eventTypeName")
    }

    @Suppress("UNCHECKED_CAST")
    return matchingHandlers as List<SagaEventHandler<SagaEvent>>
  }

  private fun handlerMatchesEvent(handler: SagaEventHandler<*>, event: SagaEvent, events: List<SagaEvent>): Boolean {
    val handlerType = ResolvableType.forClass(SagaEventHandler::class.java, handler.javaClass)

    val genericEventType = handlerType.getGeneric(0)
    if (!genericEventType.isAssignableFrom(UnionedSagaEvent::class.java)) {
      return genericEventType.isAssignableFrom(event::class.java)
    }

    return allRequiredEventsExist(
      genericEventType,
      events.map { it.javaClass.simpleName }.plus(event.javaClass.simpleName)
    )
  }

  private fun allRequiredEventsExist(unionEventType: ResolvableType, seenEventNames: List<String>): Boolean =
    seenEventNames.containsAll(unionEventType.generics.mapNotNull { it.rawClass?.simpleName })
}
