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

import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaLogAppended

class Saga(
  val name: String,
  val id: String,
  requiredEvents: List<String>,
  val compensationEvents: List<String>
) {

  private val events: MutableList<SagaEvent> = mutableListOf()
  private val requiredEvents: MutableList<String> = requiredEvents.toMutableList()

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
    return events.map { it.metadata.version }.max() ?: 0
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
    this.events.add(event)
  }

  fun hydrateEvents(events: List<SagaEvent>) {
    if (this.events.isEmpty()) {
      this.events.addAll(events)
    }
  }

  fun getEvents(): List<SagaEvent> {
    return events.toList()
  }

  fun <T : SagaEvent> getLastEvent(clazz: Class<T>): T {
    val event = events.last()
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
    log(String.format(message, replacements))
  }

  fun getLogs(): List<String> {
    return events.filterIsInstance<SagaLogAppended>().mapNotNull { it.message.user }
  }
}
