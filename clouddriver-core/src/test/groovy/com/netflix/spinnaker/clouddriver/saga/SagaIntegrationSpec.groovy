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
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.saga.model.Saga
import com.netflix.spinnaker.clouddriver.saga.repository.MemorySagaRepository
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.saga.KatoSagaBridgeDsl.StepBuilder.newStep

class SagaIntegrationSpec extends Specification {

  def "basic api"() {
    given:
    SagaProcessor sagaProcessor = new DefaultSagaProcessor(new MemorySagaRepository(), new NoopRegistry(), Mock(ApplicationEventPublisher), [])

    and:
    Saga saga = new KatoSagaBridgeDsl(new DefaultTask("id"))
      .inputs([:])
      .step(newStep("prepare", "preparing some datas")
        .fn({ state ->
          new SingleValueStepResult("preparedServerGroup", new PreparedServerGroup())
        }).build())
      .step(newStep("createSomething", "creating something")
        .fn({ state ->
          PreparedServerGroup data = state.get("preparedServerGroup")
          new SingleValueStepResult("resource", new SomeCommandCreatedResource(value: data.getDescription().join(",")) )
        }).build())
      .build()

    when:
    def result = sagaProcessor.process(saga) {
      it.get("resource", SomeCommandCreatedResource).value
    }

    then:
    result.result == "object1,object2"
  }
}

class PreparedServerGroup {
  List<String> getDescription() {
    return Arrays.asList("object1", "object2");
  }
}

class SomeCommandCreatedResource {
  String value
}
