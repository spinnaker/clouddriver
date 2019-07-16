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

import com.netflix.spinnaker.clouddriver.event.EventPublisher
import com.netflix.spinnaker.clouddriver.event.SpinEvent

/**
 * An [EventPublisher] that immediately applies any events provided to it on the current thread.
 *
 * This implementation's purpose is to keep compatibility with Clouddriver 1.x kato task behavior, where all
 * aspects of an operation were executed as a single thread process until completion or error.
 */
class LocalSagaEventPublisher(
  private val sagaService: SagaService
) : EventPublisher {
  override fun publish(event: SpinEvent) {
    if (event is SagaEvent) {
      sagaService.apply(event.aggregateType, event.aggregateId, event)
    }
  }
}
