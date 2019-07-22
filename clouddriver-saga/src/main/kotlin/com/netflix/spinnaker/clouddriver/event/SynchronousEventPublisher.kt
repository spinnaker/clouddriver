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
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class SynchronousEventPublisher : EventPublisher, ApplicationContextAware {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private lateinit var context: ApplicationContext

  override fun publish(event: SpinEvent) {
    context.getBeansOfType(EventListener::class.java).values.toList()
      .also {
        log.trace("Publishing event: $event to ${it.joinToString(",")}")
      }.forEach {
        try {
          it.onEvent(event)
        } catch (e: Exception) {
          // TODO(rz): Feels like we should try escaping early here
          log.error("EventListener generated an error", e)
        }
      }
  }

  override fun setApplicationContext(applicationContext: ApplicationContext) {
    context = applicationContext
  }
}
