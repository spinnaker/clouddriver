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
package com.netflix.spinnaker.clouddriver.saga.flow

import com.google.common.annotations.Beta
import com.netflix.spinnaker.clouddriver.saga.exceptions.IllegalSagaFlowStateException
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaFlowActionNotFoundException
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow.InjectLocation.AFTER
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow.InjectLocation.BEFORE
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * A high-level DSL to help build and visualize the workflow that a [Saga] will take towards completion.
 *
 * The simplest [Saga] is one that has a single [SagaAction]. A [SagaCompletionHandler] is optional.
 */
@Beta
class SagaFlow {

  internal val steps: MutableList<Step> = mutableListOf()
  internal var exceptionHandler: Class<out SagaExceptionHandler>? = null
  internal var completionHandler: Class<out SagaCompletionHandler<*>>? = null

  private val clock: Clock

  constructor() {
    clock = Clock.systemDefaultZone()
  }

  constructor(clock: Clock) {
    this.clock = clock
  }

  /**
   * An action to take next.
   */
  fun then(action: Class<out SagaAction<*>>): SagaFlow {
    steps.add(ActionStep(action))
    return this
  }

  /**
   * Add a new [SagaAction] into the flow at an arbitrary position in relation to another [SagaAction].
   */
  fun inject(
    location: InjectLocation,
    targetAction: Class<out SagaAction<*>>,
    action: Class<out SagaAction<*>>
  ): SagaFlow {
    val index = steps.filterIsInstance<ActionStep>().indexOfFirst { it.action == targetAction }
    if (index == -1) {
      throw SagaFlowActionNotFoundException(targetAction)
    }
    when (location) {
      BEFORE -> steps.add(index, ActionStep(action))
      AFTER -> {
        val afterIndex = index + 1
        if (afterIndex > steps.size - 1) {
          steps.add(ActionStep(action))
        } else {
          steps.add(index + 1, ActionStep(action))
        }
      }
    }
    return this
  }

  /**
   * Inject the provided [SagaAction] as the first step.
   */
  fun injectFirst(action: Class<out SagaAction<*>>): SagaFlow {
    steps.add(0, ActionStep(action))
    return this
  }

  /**
   * Define a conditional branch.
   *
   * The condition is evaluated at runtime.
   *
   * @param condition The [Predicate] that will evaluate whether or not to branch logic
   * @param builder The nested [SagaFlow] used to define the branched steps, will only be called if [condition] is true
   */
  fun on(condition: Class<out Predicate<Saga>>, builder: (SagaFlow) -> Unit): SagaFlow {
    steps.add(ConditionStep(condition, SagaFlow().also(builder)))
    return this
  }

  /**
   * Java-compatible interface.
   */
  fun on(condition: Class<out Predicate<Saga>>, builder: Consumer<SagaFlow>): SagaFlow {
    steps.add(ConditionStep(condition, SagaFlow().also { builder.accept(this) }))
    return this
  }

  /**
   * Define an await state condition step.
   *
   * An await step will yield active execution of the [Saga] until [condition] has returned true. This is different
   * than [on], in that if the test fails initially, it will retry until [ttl] expires. Should the wait expire,
   * an optional [onTimeoutBuilder] can be used to perform alternative logic.
   *
   * It is not suggested to use [onTimeoutBuilder] as an error handler that cleans up, but rather an fallback
   * branch to achieve the same result.
   *
   * @param condition The boolean [Supplier] that will evaluate if the desired awaited state has been achieved
   * @param ttl The amount of time the flow will attempt to retry [condition]
   * @param interval The amount of time between [supplier] attempts
   */
  fun await(
    condition: Class<out Function<Saga, Boolean>>,
    ttl: Duration,
    interval: Duration,
    onTimeoutBuilder: ((SagaFlow) -> Unit)?
  ): SagaFlow {
    steps.add(AwaitStep(condition, ttl, interval, onTimeoutBuilder?.let { SagaFlow().also(it) }))
    return this
  }

  /**
   * Java-compatible interface.
   */
  fun await(
    condition: Class<out Function<Saga, Boolean>>,
    ttl: Duration,
    interval: Duration,
    onTimeoutBuilder: Consumer<SagaFlow>? = null
  ): SagaFlow {
    steps.add(AwaitStep(
      condition,
      ttl,
      interval,
      onTimeoutBuilder?.let { builder ->
        SagaFlow().also { builder.accept(this) }
      }
    ))
    return this
  }

  /**
   * An optional [SagaCompletionHandler].
   *
   * @param handler The [SagaCompletionHandler] to invoke on completion
   */
  fun completionHandler(handler: Class<out SagaCompletionHandler<*>>): SagaFlow {
    completionHandler = handler
    return this
  }

  /**
   * An optional [SagaExceptionHandler].
   *
   * @param handler The [SagaExceptionHandler] to invoke when an exception is caught
   */
  fun exceptionHandler(handler: Class<out SagaExceptionHandler>): SagaFlow {
    exceptionHandler = handler
    return this
  }

  interface Step
  inner class ActionStep(val action: Class<out SagaAction<*>>) : Step
  inner class ConditionStep(val predicate: Class<out Predicate<Saga>>, val nestedBuilder: SagaFlow) : Step
  inner class AwaitStep(
    val condition: Class<out Function<Saga, Boolean>>,
    val ttl: Duration,
    val interval: Duration,
    val onTimeoutBuilder: SagaFlow?
  ) : Step {

    val startTime: Instant
      get() = _startTime ?: throw IllegalSagaFlowStateException("Await step has not started yet")

    private var _startTime: Instant? = null

    /**
     * Records the time the [AwaitStep] is initially encountered within the iterator.
     */
    fun recordStartTimeIfUnset(clock: Clock) {
      if (_startTime == null) {
        _startTime = clock.instant()
      }
    }
  }

  enum class InjectLocation {
    BEFORE,
    AFTER
  }
}
