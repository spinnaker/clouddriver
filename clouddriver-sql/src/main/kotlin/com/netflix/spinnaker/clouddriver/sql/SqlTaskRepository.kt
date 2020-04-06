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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.clouddriver.data.task.TaskState.FAILED
import com.netflix.spinnaker.clouddriver.data.task.TaskState.STARTED
import com.netflix.spinnaker.kork.sql.routing.withPool
import de.huxhorn.sulky.ulid.ULID
import java.time.Clock
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.sql
import org.slf4j.LoggerFactory

class SqlTaskRepository(
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val poolName: String
) : TaskRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  init {
    log.info("Using ${javaClass.simpleName} with pool $poolName")
  }

  override fun create(phase: String, status: String): Task {
    return create(phase, status, ulid.nextULID())
  }

  override fun create(phase: String, status: String, clientRequestId: String): Task {
    var task = SqlTask(ulid.nextULID(), ClouddriverHostname.ID, clientRequestId, clock.millis(), mutableSetOf(), this)
    val historyId = ulid.nextULID()

    withPool(poolName) {
      jooq.transactional { ctx ->
        val existingTask = getByClientRequestId(clientRequestId)
        if (existingTask != null) {
          task = existingTask as SqlTask
          addToHistory(ctx, historyId, existingTask.id, FAILED, phase, "Duplicate of $clientRequestId")
        } else {
          val pairs = mapOf(
            field("id") to task.id,
            field("owner_id") to task.ownerId,
            field("request_id") to task.requestId,
            field("created_at") to task.startTimeMs,
            field("saga_ids") to mapper.writeValueAsString(task.sagaIds)
          )

          ctx.insertInto(tasksTable, *pairs.keys.toTypedArray()).values(*pairs.values.toTypedArray()).execute()
          addToHistory(ctx, historyId, task.id, STARTED, phase, status)
        }
      }
      // TODO(rz): So janky and bad.
      task.refresh(true)
    }

    return task
  }

  fun updateSagaIds(task: Task) {
    return withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.update(tasksTable)
          .set(field("saga_ids"), mapper.writeValueAsString(task.sagaIds))
          .where(field("id").eq(task.id))
          .execute()
      }
    }
  }

  override fun get(id: String): Task? {
    return retrieveInternal(id)
  }

  override fun getByClientRequestId(clientRequestId: String): Task? {
    return withPool(poolName) {
      jooq.read {
        it.select(field("id"))
          .from(tasksTable)
          .where(field("request_id").eq(clientRequestId))
          .fetchOne("id", String::class.java)
          ?.let { taskId ->
            retrieveInternal(taskId)
          }
      }
    }
  }

  override fun list(): MutableList<Task> {
    return withPool(poolName) {
      jooq.read {
        runningTaskIds(it, false).let { taskIds ->
          retrieveInternal(field("id").`in`(*taskIds), field("task_id").`in`(*taskIds)).toMutableList()
        }
      }
    }
  }

  override fun listByThisInstance(): MutableList<Task> {
    return withPool(poolName) {
      jooq.read {
        runningTaskIds(it, true).let { taskIds ->
          retrieveInternal(field("id").`in`(*taskIds), field("task_id").`in`(*taskIds)).toMutableList()
        }
      }
    }
  }

  internal fun addResultObjects(results: List<Any>, task: Task) {
    val resultIdPairs = results.map { ulid.nextULID() to it }.toMap()

    withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.select(taskStatesFields)
          .from(taskStatesTable)
          .where(field("task_id").eq(task.id))
          .orderBy(field("created_at").asc())
          .limit(1)
          .fetchTaskStatus()
          ?.run {
            ensureUpdateable()
          }

        resultIdPairs.forEach { result ->
          ctx.insertInto(taskResultsTable, listOf(field("id"), field("task_id"), field("body")))
            .values(listOf(
              result.key,
              task.id,
              mapper.writeValueAsString(result.value)
            ))
            .execute()
        }
      }
    }
  }

  internal fun updateCurrentStatus(task: Task, phase: String, status: String) {
    val historyId = ulid.nextULID()
    withPool(poolName) {
      jooq.transactional { ctx ->
        val state = selectLatestState(ctx, task.id)
        addToHistory(ctx, historyId, task.id, state?.state ?: STARTED, phase, status.take(MAX_STATUS_LENGTH))
      }
    }
  }

  private fun addToHistory(ctx: DSLContext, id: String, taskId: String, state: TaskState, phase: String, status: String) {
    ctx
      .insertInto(
        taskStatesTable,
        listOf(field("id"), field("task_id"), field("created_at"), field("state"), field("phase"), field("status"))
      )
      .values(listOf(id, taskId, clock.millis(), state.toString(), phase, status))
      .execute()
  }

  internal fun updateState(task: Task, state: TaskState) {
    val historyId = ulid.nextULID()
    withPool(poolName) {
      jooq.transactional { ctx ->
        selectLatestState(ctx, task.id)?.let {
          addToHistory(ctx, historyId, task.id, state, it.phase, it.status)
        }
      }
    }
  }

  internal fun retrieveInternal(taskId: String): Task? {
    return retrieveInternal(field("id").eq(taskId), field("task_id").eq(taskId)).firstOrNull()
  }

  private fun retrieveInternal(condition: Condition, relationshipCondition: Condition? = null): Collection<Task> {
    val tasks = mutableSetOf<Task>()

    // TODO: AWS Aurora enforces REPEATABLE_READ on replicas. Kork's dataSourceConnectionProvider sets READ_COMMITTED
    //  on every connection acquire - need to change this so running on !aurora will behave consistently.
    //  REPEATABLE_READ is correct here.
    withPool(poolName) {
      jooq.transactional { ctx ->
        /**
         *  (select id as task_id, owner_id, request_id, created_at, saga_ids, null as body, null as state, null as phase, null as status from tasks_copy where id = '01D2H4H50VTF7CGBMP0D6HTGTF')
         *  UNION ALL
         *  (select task_id, null as owner_id, null as request_id, null as created_at, null as saga_ids, null as body, state, phase, status from task_states_copy where task_id = '01D2H4H50VTF7CGBMP0D6HTGTF')
         *  UNION ALL
         *  (select task_id, null as owner_id, null as request_id, null as created_at, null as saga_ids, body, null as state, null as phase, null as status from task_results_copy where task_id = '01D2H4H50VTF7CGBMP0D6HTGTF')
         */
        tasks.addAll(
          ctx
            .select(
              field("id").`as`("task_id"),
              field("owner_id"),
              field("request_id"),
              field("created_at"),
              field("saga_ids"),
              field(sql("null")).`as`("body"),
              field(sql("null")).`as`("state"),
              field(sql("null")).`as`("phase"),
              field(sql("null")).`as`("status")
            )
            .from(tasksTable)
            .where(condition)
            .unionAll(
              ctx
                .select(
                  field("task_id"),
                  field(sql("null")).`as`("owner_id"),
                  field(sql("null")).`as`("request_id"),
                  field(sql("null")).`as`("created_at"),
                  field(sql("null")).`as`("saga_ids"),
                  field(sql("null")).`as`("body"),
                  field("state"),
                  field("phase"),
                  field("status")
                )
                .from(taskStatesTable)
                .where(relationshipCondition ?: condition)
            )
            .unionAll(
              ctx
                .select(
                  field("task_id"),
                  field(sql("null")).`as`("owner_id"),
                  field(sql("null")).`as`("request_id"),
                  field(sql("null")).`as`("created_at"),
                  field(sql("null")).`as`("saga_ids"),
                  field("body"),
                  field(sql("null")).`as`("state"),
                  field(sql("null")).`as`("phase"),
                  field(sql("null")).`as`("status")
                )
                .from(taskResultsTable)
                .where(relationshipCondition ?: condition)
            )
            .fetchTasks()
        )
      }
    }

    return tasks
  }

  private fun selectLatestState(ctx: DSLContext, taskId: String): DefaultTaskStatus? {
    return withPool(poolName) {
      ctx.select(taskStatesFields)
        .from(taskStatesTable)
        .where(field("task_id").eq(taskId))
        .orderBy(field("created_at").desc())
        .limit(1)
        .fetchTaskStatus()
    }
  }

  /**
   * Since task statuses are insert-only, we first need to find the most
   * recent status record for each task ID and the filter that result set
   * down to the ones that are running.
   */
  private fun runningTaskIds(ctx: DSLContext, thisInstance: Boolean): Array<String> {
    return withPool(poolName) {
      val baseQuery = ctx.select()
        .from(taskStatesTable.`as`("a"))
        .innerJoin(
          ctx.select(field("task_id"), DSL.max(field("created_at")).`as`("created"))
            .from(taskStatesTable)
            .groupBy(field("task_id"))
            .asTable("b")
        ).on(sql("a.task_id = b.task_id and a.created_at = b.created"))

      val select = if (thisInstance) {
        baseQuery
          .innerJoin(tasksTable.`as`("t")).on(field("a.task_id").eq("t.id"))
          .where(
            field("a.owner_id").eq(ClouddriverHostname.ID)
              .and(field("a.state").eq(TaskState.STARTED.toString()))
          )
      } else {
        baseQuery.where(field("a.state").eq(TaskState.STARTED.toString()))
      }

      select
        .fetch("task_id", String::class.java)
        .toTypedArray()
    }
  }

  private fun Select<out Record>.fetchTasks() =
    TaskMapper(this@SqlTaskRepository, mapper).map(fetch().intoResultSet())

  private fun Select<out Record>.fetchTaskStatuses() =
    TaskStatusMapper().map(fetch().intoResultSet())

  private fun Select<out Record>.fetchTaskStatus() =
    fetchTaskStatuses().firstOrNull()

  companion object {
    private val ulid = ULID()
    private val MAX_STATUS_LENGTH = 10_000
  }
}
