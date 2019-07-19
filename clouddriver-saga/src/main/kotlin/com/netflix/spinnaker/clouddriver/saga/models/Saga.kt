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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaLogAppended
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

  var dirty: Boolean = false
    private set(value) {
      field = value
    }

  var completed: Boolean = false
    set(value) {
      if (value != field) {
        dirty = true
        field = value
      }
    }

  var compensating: Boolean = false
    set(value) {
      if (value != field) {
        dirty = true
        field = value
      }
    }

  fun getVersion(): Long {
    return events.map { it.metadata.originatingVersion }.max()?.let { it + 1 } ?: 0
  }

  fun addRequiredEvent(event: String) {
    dirty = true
    requiredEvents.add(event)
  }

  fun getRequiredEvents(): List<String> {
    return requiredEvents.toList()
  }

  fun addEvent(event: SagaEvent) {
    dirty = true
    this.pendingEvents.add(event)
  }

  @VisibleForTesting
  fun addEventForTest(event: SagaEvent) {
    this.events.add(event)
  }

  fun hydrateEvents(events: List<SagaEvent>) {
    if (this.events.isEmpty()) {
      this.events.addAll(events)
    }
  }

  fun getSequence(): Long = sequence

  fun setSequence(appliedEventVersion: Long) {
    if (sequence > appliedEventVersion) {
      throw SagaSystemException("Attempting to set the saga sequence to an event version in the past")
    }
    sequence = appliedEventVersion
    dirty = true
  }

  fun getEvents(): List<SagaEvent> {
    return events.toList()
  }

  fun getPendingEvents(): List<SagaEvent> {
    val pending = mutableListOf<SagaEvent>()
    pending.addAll(pendingEvents)
    pendingEvents.clear()
    return pending.toList()
  }

  fun <T : SagaEvent> getLastEvent(clazz: Class<T>): T? {
    // TODO(rz): Should change this to return the last event of the given type instead.
    val event = events.lastOrNull() ?: return null
    if (!clazz.isAssignableFrom(event.javaClass)) {
      throw IllegalStateException("Expected ${clazz.simpleName}, got ${event.javaClass.simpleName}")
    }
    @Suppress("UNCHECKED_CAST")
    return event as T
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
