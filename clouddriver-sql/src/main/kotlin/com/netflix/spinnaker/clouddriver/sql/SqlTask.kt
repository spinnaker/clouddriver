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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.data.task.SagaId
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayOutput
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskOutput
import com.netflix.spinnaker.clouddriver.data.task.TaskState

import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * TOOD(rz): Refactor 'river to not use an active record pattern. This sucks.
 */
class SqlTask(
  private val id: String,
  @JsonIgnore internal val ownerId: String,
  @JsonIgnore internal val requestId: String,
  @JsonIgnore internal val startTimeMs: Long,
  private val sagaIds: MutableSet<SagaId>,
  private val repository: SqlTaskRepository
) : Task {

  companion object {
    private val log = LoggerFactory.getLogger(SqlTask::class.java)
  }

  private var resultObjects: MutableList<Any> = mutableListOf()
  private var history: MutableList<Status> = mutableListOf()
  private var taskOutputs: MutableList<TaskOutput> = mutableListOf()

  private val dirty = AtomicBoolean(false)

  override fun getId() = id
  override fun getOwnerId() = ownerId
  override fun getStartTimeMs() = startTimeMs
  override fun getRequestId() = requestId

  override fun getResultObjects(): MutableList<Any> {
    refresh()
    return resultObjects
  }

  override fun addResultObjects(results: MutableList<Any>) {
    if (results.isEmpty()) {
      return
    }
    this.dirty.set(true)
    repository.addResultObjects(results, this)
    log.debug("Added {} results to task {}", results.size, id)
  }

  override fun getHistory(): List<Status> {
    refresh()

    return history.map { TaskDisplayStatus(it) }
  }

  override fun getStatus(): Status? {
    refresh()

    return history.lastOrNull()
  }

  override fun updateStatus(phase: String, status: String) {
    this.dirty.set(true)
    repository.updateCurrentStatus(this, phase, status)
    log.debug("Updated status for task {} phase={} status={}", id, phase, status)
  }

  override fun complete() {
    this.dirty.set(true)
    repository.updateState(this, TaskState.COMPLETED)
    log.debug("Set task {} as complete", id)
  }

  override fun fail() {
    this.dirty.set(true)
    repository.updateState(this, TaskState.FAILED)
  }

  override fun fail(retryable: Boolean) {
    this.dirty.set(true)
    repository.updateState(this, if (retryable) TaskState.FAILED_RETRYABLE else TaskState.FAILED)
  }

  override fun addSagaId(sagaId: SagaId) {
    this.dirty.set(true)
    sagaIds.add(sagaId)
    repository.updateSagaIds(this)
    log.debug("Added sagaId with name={} and id={} to task={}", sagaId.name, sagaId.id, id)
  }

  override fun getSagaIds(): MutableSet<SagaId> {
    return sagaIds
  }

  override fun hasSagaIds(): Boolean {
    return sagaIds.isNotEmpty()
  }

  override fun retry() {
    this.dirty.set(true)
    repository.updateState(this, TaskState.STARTED)
  }

  override fun getOutputs(): List<TaskOutput> {
    refresh()
    return taskOutputs
  }

  override fun updateOutput(manifestName: String, phase: String, stdOut: String, stdError: String) {
    this.dirty.set(true)
    repository.updateOutput( TaskDisplayOutput(manifestName, phase, stdOut, stdError),this)
    log.debug("Updated output for task {} for manifest {} for phase {} ", id, manifestName, phase)
  }

  internal fun hydrateResultObjects(resultObjects: MutableList<Any>) {
    this.dirty.set(false)
    this.resultObjects = resultObjects
  }

  internal fun hydrateHistory(history: MutableList<Status>) {
    this.dirty.set(false)
    this.history = history
  }

  internal fun hydrateTaskOutputs(taskOutputs: MutableList<TaskOutput>) {
    this.dirty.set(false)
    this.taskOutputs = taskOutputs
  }

  internal fun refresh(force: Boolean = false) {
    if (this.dirty.getAndSet(false) || force) {
      val task = repository.retrieveInternal(this.id)
      if (task != null) {
        history.clear()
        resultObjects.clear()
        taskOutputs.clear()
        history.addAll(task.history)
        resultObjects.addAll(task.resultObjects)
        taskOutputs.addAll(task.outputs)
      }
    }
  }
}
