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
package com.netflix.spinnaker.clouddriver.event

import com.netflix.spinnaker.clouddriver.event.SpringEventPublisher.SpinEventConverter
import org.springframework.context.ApplicationEventPublisher

/**
 * For each [SpinEventConverter], the event will be converted and the converted value will be emitted.
 * The raw [SpinEvent] is always published as-is in addition to any converted [SpinEvent]s.
 */
class SpringEventPublisher(
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val eventConverters: List<SpinEventConverter>
) : EventPublisher {
  override fun publish(event: SpinEvent) {
    applicationEventPublisher.publishEvent(event)
    eventConverters.map { it.convert(event) }.forEach { applicationEventPublisher.publishEvent(it) }
  }

  /**
   * Allows converting the a SpinEvent into any other another kind of Event.
   */
  interface SpinEventConverter {
    fun convert(event: SpinEvent): Any
  }
}
