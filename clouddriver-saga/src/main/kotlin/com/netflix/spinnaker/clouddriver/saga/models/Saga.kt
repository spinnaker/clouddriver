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
import com.google.common.annotations.Beta
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.SagaCompleted
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaRollbackStarted
import com.netflix.spinnaker.clouddriver.saga.SagaLogAppended
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException
import org.slf4j.LoggerFactory

/**
 * The primary domain model of the Saga framework.
 *
 * @param name The name of the Saga type. This should be shared across all same-type Sagas (e.g. aws deploys)
 * @param id The Saga instance ID
 * @param sequence An internal counter used for tracking a Saga's position in an event log
 */
@Beta
class Saga(
  val name: String,
  val id: String,
  private var sequence: Long = 0
) {

  constructor(name: String, id: String) : this(name, id, 0)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val events: MutableList<SagaEvent> = mutableListOf()
  private val pendingEvents: MutableList<SagaEvent> = mutableListOf()

  internal fun complete(success: Boolean = true) {
    addEvent(SagaCompleted(
      name,
      id,
      success
    ))
  }

  fun isComplete(): Boolean = events.filterIsInstance<SagaCompleted>().isNotEmpty()

  fun isCompensating(): Boolean = events.filterIsInstance<SagaRollbackStarted>().isNotEmpty()

  fun getVersion(): Long {
    return events.map { it.metadata.originatingVersion }.max()?.let { it + 1 } ?: 0
  }

  fun addEvent(event: SagaEvent) {
    this.pendingEvents.add(event)
  }

  internal fun completed(command: Class<SagaCommand>): Boolean {
    return getEvents().filterIsInstance<SagaCommandCompleted>().any { it.matches(command) }
  }

  internal fun getNextCommand(requiredCommand: Class<SagaCommand>): SagaCommand? {
    return getEvents()
      .filterIsInstance<SagaCommand>()
      .filterNot { completed(it.javaClass) }
      .firstOrNull { requiredCommand.isAssignableFrom(it.javaClass) }
  }

  internal fun hasUnappliedCommands(): Boolean {
    return getEvents().plus(pendingEvents)
      .filterIsInstance<SagaCommand>()
      .filterNot { completed(it.javaClass) }
      .any()
  }

  @VisibleForTesting
  fun addEventForTest(event: SagaEvent) {
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
      throw SagaSystemException("Attempted to set Saga sequence to an event version in the past " +
        "(current: $sequence, applying: $appliedEventVersion)")
    }
    sequence = appliedEventVersion
  }

  @JsonIgnoreProperties("saga")
  fun getEvents(): List<SagaEvent> {
    return events.toList()
  }

  @JsonIgnore
  @VisibleForTesting
  fun getPendingEvents(flush: Boolean = true): List<SagaEvent> {
    val pending = mutableListOf<SagaEvent>()
    pending.addAll(pendingEvents)
    if (flush) {
      pendingEvents.clear()
    }
    return pending.toList()
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
