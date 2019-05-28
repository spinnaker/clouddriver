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
package com.netflix.spinnaker.clouddriver.saga.model

import com.netflix.spinnaker.clouddriver.saga.SingleValueStepResult
import com.netflix.spinnaker.clouddriver.saga.StepResult
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class DefaultSagaStateSpec extends Specification {

  @Subject
  SagaState subject = new DefaultSagaState(
    Instant.EPOCH,
    SagaStatus.RUNNING,
    [myString: "myValue", myStringInt: "1", anObject: [nested: "nested value!"]],
    [something: [hello: "runtime"]],
  )

  def "get persisted state data"() {
    expect:
    subject.persistedState.size() == 3
    subject.get("myString") == "myValue"
    subject.get("myString", String) == "myValue"
    subject.get("myStringInt") == "1"
    subject.get("anObject") == [nested: "nested value!"]
    subject.get("no exist") == null
  }

  def "put and get runtime data"() {
    given:
    subject.put("runtimez", "very yes")

    expect:
    subject.get("myString") == "myValue"
    subject.get("runtimez") == "very yes"
    !subject.persistedState.containsKey("runtimez")
  }

  def "records logs in fifo order"() {
    when:
    subject.appendLog("first")
    subject.appendLog("second")

    then:
    subject.getLogs().size() == 2
    subject.getLogs()[0] == "first"
  }

  def "merging a step result returns a new state, overwriting duplicate persisted store keys"() {
    given:
    StepResult simple = new SingleValueStepResult("anObject", ["now", "a", "list"])

    when:
    def result = subject.merge(simple)

    then:
    subject.get("anObject") == [nested: "nested value!"]
    result.right == subject
    result.right != result.left
    result.right.version != result.left.version
    result.left.persistedState.size() == 3
    result.left.get("anObject") == ["now", "a", "list"]
  }
}
