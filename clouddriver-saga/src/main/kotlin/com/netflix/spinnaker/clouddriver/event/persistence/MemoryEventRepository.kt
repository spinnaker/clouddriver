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
package com.netflix.spinnaker.clouddriver.event.persistence

import com.netflix.spinnaker.clouddriver.event.EventPublisher
import com.netflix.spinnaker.clouddriver.event.models.Aggregate
import com.netflix.spinnaker.clouddriver.event.models.EventMetadata
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class MemoryEventRepository(
  private val eventPublisher: EventPublisher
) : EventRepository {

  // TODO(rz): errr... guava cache?
  private val aggregates: ConcurrentMap<String, Aggregate> = ConcurrentHashMap()
  private val events: ConcurrentMap<Aggregate, MutableList<SagaEvent>> = ConcurrentHashMap()

  override fun save(aggregateType: String, aggregateId: String, originatingVersion: Long, events: List<SagaEvent>) {
    val aggregate = getAggregate(aggregateType, aggregateId, originatingVersion)

    if (aggregate.version != originatingVersion) {
      throw ConcurrentModificationException("UH OH")
    }

    events.forEachIndexed { index, sagaEvent ->
      // TODO(rz): Plugin more metadata (provenance, serviceVersion, etc)
      sagaEvent.metadata = EventMetadata(
        aggregate.version + (index + 1)
      )
    }

    this.events[aggregate]!!.addAll(events)

    events.forEach { eventPublisher.publish(it) }

    cleanup()
  }

  override fun list(aggregateType: String, aggregateId: String): List<SagaEvent> {
    return getAggregate(aggregateType, aggregateId).let { events[it] } ?: emptyList()
  }

  private fun getAggregate(aggregateType: String, aggregateId: String, originatingVersion: Long? = null): Aggregate {
    val key = "$aggregateType$aggregateId"

    val agg = aggregates[key]
    if (agg != null) {
      return agg
    }

    if (originatingVersion != null && originatingVersion > 0) {
      throw IllegalStateException("UH OH")
    }

    val aggregate = Aggregate(
      aggregateId,
      aggregateType,
      0L
    )

    this.events[aggregate] = mutableListOf()

    return aggregate
  }

  private fun cleanup() {
    // TODO(rz): A ticking timebomb of horrible bugs, remove aggregates by oldest event age instead
    val randomMagicNumber = 5_000
    if (aggregates.size > randomMagicNumber) {
      aggregates.keys.toList().subList(0, randomMagicNumber).forEach {
        aggregates.remove(it)?.let { aggregate ->
          events.remove(aggregate)
        }
      }
    }
  }
}
