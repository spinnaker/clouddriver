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
 */
package com.netflix.spinnaker.clouddriver.event

import org.slf4j.LoggerFactory

class SynchronousEventPublisher : EventPublisher {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val listeners: MutableList<EventListener> = mutableListOf()

  override fun register(listener: EventListener) {
    listeners.add(listener)
  }

  override fun publish(event: SpinEvent) {
    listeners.forEach {
      try {
        it.onEvent(event)
      } catch (e: Exception) {
        log.error("EventListener generated an error", e)
      }
    }
  }
}
