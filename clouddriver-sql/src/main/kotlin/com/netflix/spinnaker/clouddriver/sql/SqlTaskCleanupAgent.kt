/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.data.task.TaskState.COMPLETED
import com.netflix.spinnaker.clouddriver.data.task.TaskState.FAILED
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlTaskCleanupAgentProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.time.Clock
import java.util.Arrays
import java.util.concurrent.TimeUnit
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.slf4j.LoggerFactory

/**
 * Cleans up completed Tasks after a configurable TTL.
 */
class SqlTaskCleanupAgent(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val registry: Registry,
  private val properties: SqlTaskCleanupAgentProperties
) : RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(javaClass)

  private val deletedId = registry.createId("sql.taskCleanupAgent.deleted")
  private val timingId = registry.createId("sql.taskCleanupAgent.timing")

  override fun run() {
    withPool(ConnectionPools.TASKS.value) {
      val candidates = jooq.read { j ->
        val candidates = j.select(field("id"), field("task_id"))
          .from(taskStatesTable)
          .where(
            field("state").`in`(COMPLETED.toString(), FAILED.toString())
              .and(
                field("created_at").lessOrEqual(
                  clock.instant().minusMillis(properties.completedTtlMs).toEpochMilli()
                )
              )
          )
          .fetch()

        val candidateTaskIds = candidates.map { r -> r.field("task_id")?.getValue(r)?.toString() }
          .filterNotNull()
          .toList()

        val candidateTaskStateIds = mutableListOf<String>()
        val candidateResultIds = mutableListOf<String>()
        val candidateOutputIds = mutableListOf<String>()

        if (candidateTaskIds.isNotEmpty()) {
          candidateTaskIds.chunked(properties.batchSize) { chunk ->
            candidateTaskStateIds.addAll(
              j.select(field("id"))
                .from(taskStatesTable)
                .where(field("task_id").`in`(*chunk.toTypedArray()))
                .fetch("id", String::class.java)
                .filterNotNull()
            )

            candidateResultIds.addAll(
              j.select(field("id"))
                .from(taskResultsTable)
                .where(field("task_id").`in`(*chunk.toTypedArray()))
                .fetch("id", String::class.java)
                .filterNotNull()
            )

            candidateOutputIds.addAll(
              j.select(field("id"))
                .from(taskOutputsTable)
                .where(field("task_id").`in`(*chunk.toTypedArray()))
                .fetch("id", String::class.java)
                .filterNotNull()
            )
          }
        }

        CleanupCandidateIds(
          taskIds = candidateTaskIds,
          stateIds = candidateTaskStateIds,
          resultIds = candidateResultIds,
          outputIds = candidateOutputIds
        )
      }

      if (candidates.hasAny()) {
        log.info(
          "Cleaning up {} completed tasks ({} states, {} result, {} output objects)",
          candidates.taskIds.size,
          candidates.stateIds.size,
          candidates.resultIds.size,
          candidates.outputIds.size
        )

        registry.timer(timingId).record {
          candidates.resultIds.chunked(properties.batchSize) { chunk ->
            jooq.transactional { ctx ->
              ctx.deleteFrom(taskResultsTable)
                .where(field("id").`in`(*chunk.toTypedArray()))
                .execute()
            }
          }

          candidates.stateIds.chunked(properties.batchSize) { chunk ->
            jooq.transactional { ctx ->
              ctx.deleteFrom(taskStatesTable)
                .where(field("id").`in`(*chunk.toTypedArray()))
                .execute()
            }
          }

          candidates.outputIds.chunked(properties.batchSize) { chunk ->
            jooq.transactional { ctx ->
              ctx.deleteFrom(taskOutputsTable)
                .where(field("id").`in`(*chunk.toTypedArray()))
                .execute()
            }
          }

          candidates.taskIds.chunked(properties.batchSize) { chunk ->
            jooq.transactional { ctx ->
              ctx.deleteFrom(tasksTable)
                .where(field("id").`in`(*chunk.toTypedArray()))
                .execute()
            }
          }
        }

        registry.counter(deletedId).increment(candidates.taskIds.size.toLong())
      }
    }
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(3)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3)
  }
}

private data class CleanupCandidateIds(
  val taskIds: List<String>,
  val stateIds: List<String>,
  val resultIds: List<String>,
  val outputIds: List<String>
) {
  fun hasAny() = taskIds.isNotEmpty()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CleanupCandidateIds

    if (taskIds.size != other.taskIds.size || !taskIds.containsAll(other.taskIds)) return false
    if (stateIds.size != other.stateIds.size || !stateIds.containsAll(other.stateIds)) return false
    if (resultIds.size != other.resultIds.size || !resultIds.containsAll(other.resultIds)) return false
    if (outputIds.size != other.outputIds.size || !outputIds.containsAll(other.outputIds)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = Arrays.hashCode(taskIds.toTypedArray())
    result = 31 * result + Arrays.hashCode(stateIds.toTypedArray())
    result = 31 * result + Arrays.hashCode(resultIds.toTypedArray())
    result = 31 * result + Arrays.hashCode(outputIds.toTypedArray())
    return result
  }
}
