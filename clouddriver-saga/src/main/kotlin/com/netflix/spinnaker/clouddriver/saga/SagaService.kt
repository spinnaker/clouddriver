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

import com.google.common.annotations.Beta
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

/**
 * The brains for the Saga framework, it's entire purpose is to apply a single event and handle the overall business
 * rules of how Saga events should be handled. By staying entirely out of the workflow aspects, a Saga can lay the
 * tracks out in front of itself at runtime.
 *
 * For every [SagaEvent], there is 0 to N [SagaEventHandler]. A [SagaEventHandler] can require 1 to N prerequisite
 * [SagaEvent]s before being invoked, similarly each [SagaEventHandler] can emit 0 to N events, allowing complex
 * fork & join operations.
 *
 * A Saga must have all of its required events present in the event log before it is considered completed and
 * similarly, must have all of its compensation events present in the event log before it is considered rolled back.
 *
 * When creating a new saga, it should first be saved, then applied. Saga changes will be saved automatically
 * by the [SagaService] once it has been initially created.
 *
 * ```
 * val saga = Saga(
 *   name = "aws://v1.Deploy",
 *   id = "my-correlation-id",
 *   requiredEvents = listOf(
 *     Front50AppLoaded::javaClass.simpleName,
 *     AmazonDeployCreated::javaClass.simpleName,
 *     // ...
 *   ),
 *   compensationEvents = listOf(
 *     // ...
 *   )
 * )
 *
 * sagaService.save(saga)
 * sagaService.apply(saga.name, saga.id, AwsDeployCreated(description, priorOutputs)
 * ```
 *
 * TODO(rz): Metrics
 */
@Beta
class SagaService(
  private val sagaRepository: SagaRepository,
  private val eventRepository: EventRepository,
  private val eventHandlerProvider: SagaEventHandlerProvider,
  private val registry: Registry
) : ApplicationContextAware {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private lateinit var applicationContext: ApplicationContext

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    this.applicationContext = applicationContext
  }

  /**
   * TODO(rz): A little save-happy on Sagas in this method
   * TODO(rz): Exception handling
   * TODO(rz): Implement input checksum verification (somewhere? Maybe not in here...)
   */
  fun apply(sagaName: String, sagaId: String, event: SagaEvent) {
    log.debug("Applying $sagaName/$sagaId: $event")

    val saga = get(sagaName, sagaId)

    val events = getEvents(sagaName, sagaId)

    if (isDuplicate(events, event)) {
      log.info("Received duplicate event for saga '${saga.name}/${saga.id}', skipping: ${event.javaClass.simpleName}")
      return
    }

    if (!isEventOutOfOrder(saga, events, event)) {
      log.error("Received out-of-order event for saga '${saga.name}/${saga.id}', throwing away: ${event.javaClass.simpleName}")
      // TODO(rz): Worth alerting on this
      return
    }

    val handlers = eventHandlerProvider.getMatching(saga, event)
    if (handlers.isEmpty()) {
      log.warn("No EventHandlers found for event, ignoring: ${event.javaClass.simpleName}")
      return
    }

    handlers.forEach {
      applySingleHandler(it, saga, event)
    }

    if (allEventsOccurred(saga)) {
      log.debug("All required events have occurred, completing: $sagaName/$sagaId")
      saga.completed = true
      sagaRepository.save(saga)
      handlers.forEach { it.finalize(event, saga) }
    }

    // Check for an error event last: It's possible an event handler has been provided to handle it explicitly
    if (isErrorEvent(saga, event)) {
      log.warn("An error event has been emitted, compensating: $sagaName/$sagaId")
      saga.compensating = true
      sagaRepository.save(saga)
      handlers.forEach { it.compensate(event, saga) }
    }
  }

  private fun applySingleHandler(handler: SagaEventHandler<SagaEvent>, saga: Saga, event: SagaEvent) {
    log.info("Applying handler '${handler.javaClass.simpleName}' on ${event.javaClass.simpleName}: " +
      "${saga.name}/{$saga.id}")
    val emittedEvents = handler.apply(event, saga)
    if (saga.dirty) {
      sagaRepository.save(saga)
    }
    eventRepository.save(saga.name, saga.id, event.metadata.version, emittedEvents)
  }

  private fun getEvents(sagaName: String, sagaId: String): List<SagaEvent> =
    eventRepository.list(sagaName, sagaId).let {
      if (it.any { e -> e !is SagaEvent }) {
        // TODO(rz): Should probably log error & filter the events out instead?
        throw SystemException("One or more events for Saga are not of SagaEvent type")
      }
      @Suppress("UNCHECKED_CAST")
      it as List<SagaEvent>
    }

  /**
   * Check if the event has already been applied.
   */
  private fun isDuplicate(events: List<SagaEvent>, event: SagaEvent): Boolean =
    events.contains(event)

  /**
   * Check if the event has been received out-of-order.
   *
   * The next event should always be n+1 of the Saga.
   */
  private fun isEventOutOfOrder(saga: Saga, events: List<SagaEvent>, event: SagaEvent): Boolean =
    saga.getVersion() + 1 == event.metadata.version

  private fun allEventsOccurred(saga: Saga): Boolean =
    eventRepository.list(saga.name, saga.id).map { it.javaClass.simpleName }.containsAll(saga.getRequiredEvents())

  private fun isErrorEvent(saga: Saga, event: SagaEvent): Boolean = event is SagaInternalErrorOccurred

  fun get(sagaName: String, sagaId: String): Saga {
    return sagaRepository.get(sagaName, sagaId) ?: throw SystemException("It should definitely exist at this point")
  }

  fun save(saga: Saga) {
    sagaRepository.save(saga)
  }
}
