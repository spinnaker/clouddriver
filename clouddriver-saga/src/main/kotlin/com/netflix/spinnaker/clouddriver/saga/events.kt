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

import com.netflix.spinnaker.clouddriver.event.SpinEvent
import com.netflix.spinnaker.clouddriver.saga.models.Saga

abstract class SagaEvent(
  sagaName: String,
  sagaId: String
) : SpinEvent(sagaName, sagaId) {
  val sagaName
    get() = aggregateType

  val sagaId
    get() = aggregateId
}

class SagaSaved(
  val saga: Saga,
  val reason: String
) : SagaEvent(saga.name, saga.id)

class SagaInternalErrorOccurred(
  sagaName: String,
  sagaId: String,
  val reason: String,
  val error: Exception? = null,
  val retryable: Boolean = true,
  val data: Map<String, String> = mapOf()
) : SagaEvent(sagaName, sagaId)

class SagaLogAppended(
  sagaName: String,
  sagaId: String,
  val message: Message,
  val diagnostics: Diagnostics? = null
) : SagaEvent(sagaName, sagaId) {

  data class Message(
    val user: String? = null,
    val system: String? = null
  )

  data class Diagnostics(
    val error: Exception? = null,
    val retryable: Boolean = true
  )
}

interface UnionedSagaEvent

class UnionSagaEvent2<A : SagaEvent, B : SagaEvent>(
  saga: Saga,
  val a: A,
  val b: B
) : SagaEvent(saga.name, saga.id), UnionedSagaEvent

class UnionSagaEvent3<A : SagaEvent, B : SagaEvent, C : SagaEvent>(
  saga: Saga,
  val a: A,
  val b: B,
  val c: C
) : SagaEvent(saga.name, saga.id), UnionedSagaEvent

class UnionSagaEvent4<A : SagaEvent, B : SagaEvent, C : SagaEvent, D : SagaEvent>(
  saga: Saga,
  val a: A,
  val b: B,
  val c: C,
  val d: D
) : SagaEvent(saga.name, saga.id), UnionedSagaEvent

class UnionSagaEvent5<A : SagaEvent, B : SagaEvent, C : SagaEvent, D : SagaEvent, E : SagaEvent>(
  saga: Saga,
  val a: A,
  val b: B,
  val c: C,
  val d: D,
  val e: E
) : SagaEvent(saga.name, saga.id), UnionedSagaEvent
