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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.saga.interceptors.DefaultSagaInterceptor
import com.netflix.spinnaker.clouddriver.saga.interceptors.SagaInterceptor
import com.netflix.spinnaker.clouddriver.saga.model.DefaultSagaState
import com.netflix.spinnaker.clouddriver.saga.model.Saga
import com.netflix.spinnaker.clouddriver.saga.model.SagaState
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus
import com.netflix.spinnaker.clouddriver.saga.repository.MemorySagaRepository
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Ignore
import spock.lang.Specification

import java.lang.reflect.Field
import java.util.function.Function

import static com.netflix.spinnaker.clouddriver.saga.TestHelper.newSaga
import static com.netflix.spinnaker.clouddriver.saga.TestHelper.newStep

class DefaultSagaProcessorSpec extends Specification {

  SagaRepository sagaRepository = new MemorySagaRepository()
  Registry registry = new NoopRegistry()
  ApplicationEventPublisher eventPublisher = Mock()

  def "should complete valid saga"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"], null) {
      newStep(it, "one") {
        it.fn({ state ->
          sleep(10)
          new SingleValueStepResult("hello", "to me")
        })
      }
      newStep(it, "two") {
        it.fn({ state ->
          sleep(10)
          new SingleValueStepResult("steptwo", "very cool")
        })
      }
    }

    when:
    String result = subject.process(saga) { it.get("hello") }.result

    then:
    noExceptionThrown()
    result == "to me"
    saga.status == SagaStatus.SUCCEEDED
    saga.latestState.persistedState == [
      hello: "to me",
      steptwo: "very cool"
    ]
  }

  def "should fail to initialize saga if input checksums do not match prior checksum"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga storedSaga = newSaga("sagaid", [hello: "world"])
    sagaRepository.upsert(storedSaga)

    Saga saga = newSaga("sagaid", [hello: "world"])

    and:
    Field f = Saga.getDeclaredField("checksum")
    f.setAccessible(true)
    f.set(saga, "nope")

    when:
    subject.process(saga) { it.get("hello") }

    then:
    thrown(DefaultSagaProcessor.InputsChecksumMismatchException)
  }

  @Ignore("Restarting sagas is supported yet")
  def "should set failed saga status to running on initialization"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"]) {
      it.status(SagaStatus.TERMINAL)
    }
    sagaRepository.upsert(saga)

    when:
    subject.process(saga) { it.get("hello") }

    then:
    noExceptionThrown()
    saga.status == SagaStatus.RUNNING
  }

  def "should skip completed steps"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"], null) {
      newStep(it, "one") {
        it.states([new DefaultSagaState(SagaStatus.SUCCEEDED, [hello: "world"])])
        it.fn({ EmptyStepResult.instance })
      }
      newStep(it, "two") {
        it.fn({ EmptyStepResult.instance })
      }
    }

    when:
    subject.process(saga) { it.get("hello") }

    then:
    noExceptionThrown()
    // This is a bit of an obtuse way of asserting this behavior...
    saga.getStep("one").createdAt == saga.getStep("one").updatedAt
    saga.getStep("two").createdAt != saga.getStep("two").updatedAt
  }

  def "should fail saga if a step is TERMINAL_FATAL"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"], {
      it.status = SagaStatus.RUNNING
    }) {
      newStep(it, "one") {
        it.states([new DefaultSagaState(SagaStatus.TERMINAL_FATAL, [hello: "world"])])
        it.fn({ EmptyStepResult.instance })
      }
    }

    when:
    subject.process(saga) { it.get("hello") }

    then:
    noExceptionThrown()
    saga.getStatus() == SagaStatus.TERMINAL_FATAL
  }

  def "should fail saga if max step attempts is reached"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"], {
      it.status = SagaStatus.RUNNING
    }) {
      newStep(it, "one") {
        it.states([new DefaultSagaState(SagaStatus.TERMINAL, [hello: "world"])])
        it.attempt = 4
        it.fn({ EmptyStepResult.instance })
      }
    }

    when:
    subject.process(saga) { it.get("hello") }

    then:
    saga.getStatus() == SagaStatus.TERMINAL_FATAL
  }

  def "should retry step on failure"() {
    given:
    SagaProcessor subject = createSubject()

    and:
    Saga saga = newSaga("sagaid", [hello: "world"], {
      it.status = SagaStatus.RUNNING
    }) {
      newStep(it, "one") {
        it.fn(new Function<SagaState, StepResult>() {
          private boolean shouldThrow = true
          @Override
          StepResult apply(SagaState sagaState) {
            sleep(5)
            if (shouldThrow) {
              shouldThrow = false
              throw new RuntimeException("Oh nooo")
            }
            return new SingleValueStepResult("hello", "friends")
          }
        })
      }
    }

    when:
    def result = subject.process(saga) { it.get("hello") }

    then:
    !result.hasError()
    result.result == "friends"
    saga.getStatus() == SagaStatus.SUCCEEDED
    saga.getStep("one").attempt == 2
    saga.getStep("one").status == SagaStatus.SUCCEEDED
  }

  private SagaProcessor createSubject() {
    return createSubject([new DefaultSagaInterceptor()])
  }

  private SagaProcessor createSubject(List<SagaInterceptor> interceptors) {
    return new DefaultSagaProcessor(sagaRepository, registry, eventPublisher, interceptors)
  }
}
