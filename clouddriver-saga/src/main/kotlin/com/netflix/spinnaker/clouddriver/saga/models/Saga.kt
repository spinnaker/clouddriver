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
package com.netflix.spinnaker.clouddriver.saga.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.saga.SagaCompleted
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaInCompensation
import com.netflix.spinnaker.clouddriver.saga.SagaLogAppended
import com.netflix.spinnaker.clouddriver.saga.SagaRequiredEventsAdded
import com.netflix.spinnaker.clouddriver.saga.SagaRequiredEventsRemoved
import com.netflix.spinnaker.clouddriver.saga.exceptions.EventNotFoundException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException

/**
 * The primary domain model of the Saga framework.
 *
 * @param name The name of the Saga type. This should be shared across all same-type Sagas (e.g. aws deploys)
 * @param id The Saga instance ID
 * @param completionHandler The bean name of the [SagaCompletionHandler] that will be called on finalization of a Saga
 *                          If no bean is provided, nothing will be automatically invoked on completion.
 * @param requiredEvents A list of event names that must be saved to the Saga before it is considered completed
 * @param compensationEvents A list of event names that must be saved for the Saga to be considered rolled back
 */
class Saga(
  val name: String,
  val id: String,
  val completionHandler: String?,
  requiredEvents: List<String>,
  val compensationEvents: List<String>,
  private var sequence: Long = 0
) {

  constructor(
    name: String,
    id: String,
    completionHandler: String?,
    requiredEvents: List<String>,
    compensationEvents: List<String>
  ) :
    this(name, id, completionHandler, requiredEvents, compensationEvents, 0)

  private val events: MutableList<SagaEvent> = mutableListOf()
  private val requiredEvents: MutableList<String> = requiredEvents.toMutableList()
  private val pendingEvents: MutableList<SagaEvent> = mutableListOf()

  fun complete(success: Boolean = true) {
    addEvent(SagaCompleted(
      name,
      id,
      success
    ))
  }

  fun isComplete(): Boolean = events.filterIsInstance<SagaCompleted>().isNotEmpty()

  fun isCompensating(): Boolean = events.filterIsInstance<SagaInCompensation>().isNotEmpty()

  fun getVersion(): Long {
    return events.map { it.metadata.originatingVersion }.max()?.let { it + 1 } ?: 0
  }

  fun addRequiredEvent(event: String) {
    requiredEvents.add(event)
    addEvent(SagaRequiredEventsAdded(
      name,
      id,
      eventNames = listOf(event)
    ))
  }

  fun removeRequiredEvent(event: String) {
    requiredEvents.remove(event)
    addEvent(SagaRequiredEventsRemoved(
      name,
      id,
      eventNames = listOf(event)
    ))
  }

  fun getRequiredEvents(): List<String> {
    return requiredEvents.toList()
  }

  fun addEvent(event: SagaEvent) {
    this.pendingEvents.add(event)
  }

  @VisibleForTesting
  internal fun addEventForTest(event: SagaEvent) {
    this.events.add(event)
  }

  internal fun hydrateEvents(events: List<SagaEvent>) {
    if (this.events.isEmpty()) {
      this.events.addAll(events)
    }
  }

  fun getSequence(): Long = sequence

  internal fun setSequence(appliedEventVersion: Long) {
    if (sequence > appliedEventVersion) {
      throw SagaSystemException("Attempting to set the saga sequence to an event version in the past")
    }
    sequence = appliedEventVersion
  }

  @JsonIgnoreProperties("saga")
  fun getEvents(): List<SagaEvent> {
    return events.toList()
  }

  @JsonIgnore
  fun getPendingEvents(flush: Boolean = true): List<SagaEvent> {
    val pending = mutableListOf<SagaEvent>()
    pending.addAll(pendingEvents)
    if (flush) {
      pendingEvents.clear()
    }
    return pending.toList()
  }

  fun <T : SagaEvent> getLastEvent(clazz: Class<T>): T? {
    try {
      @Suppress("UNCHECKED_CAST")
      return getEvents().lastOrNull { clazz.isAssignableFrom(it.javaClass) } as T?
    } catch (e: NoSuchElementException) {
      throw EventNotFoundException("Could not find event by type: ${clazz.simpleName}", e)
    }
  }

  fun log(message: String) {
    addEvent(SagaLogAppended(
      name,
      id,
      SagaLogAppended.Message(message, null),
      null
    ))
  }

  fun log(message: String, vararg replacements: Any?) {
    log(String.format(message, *replacements))
  }

  fun getLogs(): List<String> {
    return events.filterIsInstance<SagaLogAppended>().mapNotNull { it.message.user }
  }
}
