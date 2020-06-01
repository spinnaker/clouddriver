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
 *
 */
package com.netflix.spinnaker.clouddriver.saga.flow

import com.netflix.spinnaker.clouddriver.saga.AbstractSagaTest
import com.netflix.spinnaker.clouddriver.saga.Action1
import com.netflix.spinnaker.clouddriver.saga.Action2
import com.netflix.spinnaker.clouddriver.saga.Action3
import com.netflix.spinnaker.clouddriver.saga.AwaitCondition
import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.ShouldBranch
import com.netflix.spinnaker.clouddriver.saga.ShouldBranchPredicate
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaAwaitConditionTimeoutException
import com.netflix.spinnaker.kork.test.time.MutableClock
import dev.minutest.rootContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

class SagaFlowIteratorTest : AbstractSagaTest() {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("basic iterator progression") {
      val flow = SagaFlow()
        .then(Action1::class.java)
        .on(ShouldBranchPredicate::class.java) {
          it.then(Action2::class.java)
        }
        .then(Action3::class.java)

      test("iterates top-level actions only") {
        val subject = createSubject(flow)
        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action1>()
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action3>()
          that(subject.hasNext()).isFalse()
        }
      }

      test("iterates conditional actions") {
        val subject = createSubject(flow)

        saga.addEventForTest(ShouldBranch())

        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action1>()
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action2>()
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action3>()
          that(subject.hasNext()).isFalse()
        }
      }

      test("seeks iterator with partially applied saga") {
        val subject = createSubject(flow)

        saga.addEventForTest(SagaCommandCompleted("doAction1"))
        saga.addEventForTest(ShouldBranch())

        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action2>()
          that(subject.next()).get { action }.isA<Action3>()
          that(subject.hasNext()).isFalse()
        }
      }
    }

    context("await condition") {
      val flow = SagaFlow()
        .then(Action1::class.java)
        .await(AwaitCondition::class.java, Duration.ofMillis(10), Duration.ofMillis(5))
        .then(Action2::class.java)

      val clock = MutableClock(Instant.now())

      test("does not iterate to next step while condition is false") {
        val subject = createSubject(flow, clock)

        saga.addEventForTest(SagaCommandCompleted("doAction1"))

        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).and {
            get { action }.isNull()
            get { control }.isNotNull().and {
              get { delay.toMillis() }.isEqualTo(5)
            }
          }
        }

        clock.incrementBy(Duration.ofMillis(5))

        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).and {
            get { action }.isNull()
            get { control }.isNotNull()
          }
        }
      }

      test("exception thrown after timeout and noTimeout provided") {
        val subject = createSubject(flow, clock)

        saga.addEventForTest(SagaCommandCompleted("doAction1"))
        clock.incrementBy(Duration.ofMillis(100))

        expectThrows<SagaAwaitConditionTimeoutException> { subject.hasNext() }
      }

      test("onTimeout SagaFlow run after timeout") {
        val timeoutFlow = SagaFlow()
          .then(Action1::class.java)
          .await(AwaitCondition::class.java, Duration.ofMillis(10), Duration.ofMillis(5)) {
            it.then(Action2::class.java)
          }

        val subject = createSubject(timeoutFlow, clock)

        saga.addEventForTest(SagaCommandCompleted("doAction1"))

        // First hasNext sets the startTime, then we fast-forward after the timeout
        expectThat(subject.hasNext()).isTrue()
        clock.incrementBy(Duration.ofMillis(100))

        expect {
          that(subject.hasNext()).isTrue()
          that(subject.next()).get { action }.isA<Action2>()
        }
      }
    }
  }

  private inner class Fixture : BaseSagaFixture() {

    val awaitCondition = AwaitCondition()

    init {
      sagaRepository.save(saga)
      registerBeans(applicationContext, awaitCondition)
    }

    fun createSubject(flow: SagaFlow, clock: Clock = Clock.systemDefaultZone()) =
      SagaFlowIterator(sagaRepository, applicationContext, clock, saga, flow)
  }
}
