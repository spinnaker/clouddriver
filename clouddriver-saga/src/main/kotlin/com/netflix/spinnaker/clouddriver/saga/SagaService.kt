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
import com.netflix.spinnaker.clouddriver.event.EventListener
import com.netflix.spinnaker.clouddriver.event.EventPublisher
import com.netflix.spinnaker.clouddriver.event.SpinEvent
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.saga.exceptions.InvalidSagaCompletionHandlerException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanNotOfRequiredTypeException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
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
 *   completionHandler = "myCompletionHandlerBeanName",
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
  eventPublisher: EventPublisher,
  private val registry: Registry
) : ApplicationContextAware, EventListener {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private lateinit var applicationContext: ApplicationContext

  init {
    eventPublisher.register(this)
  }

  override fun onEvent(event: SpinEvent) {
    // TODO(rz): `apply` would only occur via Commands; this method could disappear
    if (event is SagaEvent) {
      apply(event)
    }
  }

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    this.applicationContext = applicationContext
  }

  /**
   * TODO(rz): A little save-happy on Sagas in this method: That will make things harder for everything.
   * TODO(rz): Exception handling
   */
  fun apply(event: SagaEvent) {
    requireSynthesizedEventMetadata(event)

    val sagaName = event.sagaName
    val sagaId = event.sagaId
    log.debug("Applying $sagaName/$sagaId: $event")

    val saga = get(sagaName, sagaId)

    if (isEventOutOfOrder(saga, event)) {
      log.warn("Received out-of-order event for saga '${saga.name}/${saga.id}', ignoring: ${event.javaClass.simpleName}")
      return
    }

    val handlers = eventHandlerProvider.getMatching(saga, event)
    if (handlers.isEmpty()) {
      log.warn("No EventHandlers found for event, ignoring: ${event.javaClass.simpleName}")
      return
    }

    val emittedEvents = handlers
      .flatMap { handler ->
        log.info("Applying handler '${handler.javaClass.simpleName}' on ${event.javaClass.simpleName}: " +
          "${saga.name}/{$saga.id}")
        handler.apply(event, saga)
      }

    if (allRequiredEventsApplied(saga)) {
      log.debug("All required events have occurred, completing: $sagaName/$sagaId")
      saga.completed = true
    }

    // Check for an error event last: It's possible an event handler has been provided to handle it explicitly
    if (isErrorEvent(saga, event)) {
      log.warn("An error event has been emitted, compensating: $sagaName/$sagaId")
      saga.compensating = true
      handlers.forEach { it.compensate(event, saga) }
    }

    saga.setSequence(event.metadata.sequence)
    sagaRepository.save(saga, emittedEvents)
  }

  private fun isEventOutOfOrder(saga: Saga, event: SagaEvent): Boolean = false
//    saga.getSequence() + 1 != event.metadata.sequence

  private fun allRequiredEventsApplied(saga: Saga): Boolean {
    val maxRequiredEventVersion = eventRepository.list(saga.name, saga.id)
      .filter { saga.getRequiredEvents().contains(it.javaClass.simpleName) }
      .map { it.metadata.sequence }
      .max()
      ?: -1
    return maxRequiredEventVersion <= saga.getSequence()
  }

  // TODO(rz): Well this code is obviously not done
  private fun isErrorEvent(saga: Saga, event: SagaEvent): Boolean = false

  private fun requireSynthesizedEventMetadata(event: SagaEvent) {
    try {
      event.metadata.originatingVersion
    } catch (e: UninitializedPropertyAccessException) {
      // If this is thrown, it's a core bug in the library.
      throw SagaSystemException("Event metadata has not been synthesized yet", e)
    }
  }

  fun get(sagaName: String, sagaId: String): Saga {
    return sagaRepository.get(sagaName, sagaId) ?: throw SagaSystemException("Saga must be saved before applying")
  }

  fun save(saga: Saga, onlyIfMissing: Boolean = false) {
    if (onlyIfMissing && sagaRepository.get(saga.name, saga.id) != null) {
      return
    }

    // Just asserting that the completion handler actually exists
    getCompletionHandler(saga)

    sagaRepository.save(saga)
  }

  fun <T> awaitCompletion(saga: Saga): T? {
    // TODO(rz): This obviously doesn't "await" anything; it's a placeholder for when the event publisher is async.
    return get(saga.name, saga.id).let { completedSaga ->
      @Suppress("UNCHECKED_CAST")
      getCompletionHandler(completedSaga)?.apply(completedSaga) as T
    }
  }

  private fun getCompletionHandler(saga: Saga): SagaCompletionHandler<*>? =
    saga.completionHandler
      ?.let { completionHandler ->
        try {
          applicationContext.getBean(completionHandler, SagaCompletionHandler::class.java)
        } catch (e: NoSuchBeanDefinitionException) {
          throw InvalidSagaCompletionHandlerException.notFound(saga.completionHandler, saga.name, e)
        } catch (e: BeanNotOfRequiredTypeException) {
          throw InvalidSagaCompletionHandlerException.invalidType(
            saga.completionHandler,
            saga.name,
            applicationContext.getType(saga.completionHandler)?.simpleName ?: "unknown",
            e
          )
        } catch (e: BeansException) {
          throw InvalidSagaCompletionHandlerException("Could not load saga completion handler", e)
        }
      }
}
