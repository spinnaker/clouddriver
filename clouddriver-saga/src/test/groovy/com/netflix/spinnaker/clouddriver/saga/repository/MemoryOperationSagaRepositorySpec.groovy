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
package com.netflix.spinnaker.clouddriver.saga.repository

import com.netflix.spinnaker.clouddriver.saga.SingleValueStepResult
import com.netflix.spinnaker.clouddriver.saga.model.DefaultSagaState
import com.netflix.spinnaker.clouddriver.saga.model.Saga
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep
import spock.lang.Specification

import java.time.Instant
/**
 * TODO(rz): More tests.
 */
class MemoryOperationSagaRepositorySpec extends Specification {

  def "upsert and retrieve operations"() {
    given:
    SagaRepository subject = new MemorySagaRepository()

    expect:
    subject.list(ListStateCriteria.none()).result.isEmpty()

    when:
    def saga = Saga.createForTest("one", [hello: "world"], [])
    def step = SagaStep.builder()
      .id("hi")
      .label("hi")
      .createdAt(Instant.now())
      .updatedAt(Instant.now())
      .states([])
      .saga(saga)
      .fn({})
      .build()
    saga.steps.add(step)
    def result = subject.upsert(saga)

    then:
    result.getId() == "one"
    subject.list(ListStateCriteria.none()).result.size() == 1

    when:
    def v2State = new DefaultSagaState(
      Instant.now(),
      SagaStatus.RUNNING,
      [hello: "friends"],
      [:],
    )
    step.states.add(v2State)

    subject.upsert(step)

    then:
    subject.list(ListStateCriteria.none()).result.size() == 1
    subject.get("one") == saga
    saga.getLatestState() == v2State

    when:
    def v3State = v2State.merge(new SingleValueStepResult("newKey", "newValue")).left
    step.states.add(v3State)

    subject.upsert(step)

    then:
    saga.getLatestState() == v3State
  }
}
