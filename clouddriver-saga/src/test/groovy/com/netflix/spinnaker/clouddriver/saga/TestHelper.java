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
package com.netflix.spinnaker.clouddriver.saga;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaDirection;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import com.netflix.spinnaker.clouddriver.saga.utils.Checksum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class TestHelper {

  public static Saga newSaga(String id, Map<String, Object> inputs) {
    return newSaga(id, inputs, sagaBuilder -> {});
  }

  public static Saga newSaga(
      String id, Map<String, Object> inputs, Consumer<Saga.SagaBuilder> customizer) {
    Instant now = Instant.now();
    Saga.SagaBuilder builder =
        Saga.builder()
            .id(id)
            .inputs(inputs)
            .checksum(Checksum.md5(inputs))
            .createdAt(now)
            .updatedAt(now)
            .owner("testing")
            .direction(SagaDirection.FORWARD)
            .status(SagaStatus.RUNNING)
            .steps(new ArrayList<>());

    if (customizer != null) {
      customizer.accept(builder);
    }

    return builder.build();
  }

  public static Saga newSaga(
      String id,
      Map<String, Object> inputs,
      Consumer<Saga.SagaBuilder> customizer,
      Consumer<Saga> sagaCustomizer) {
    Saga saga = newSaga(id, inputs, customizer);
    sagaCustomizer.accept(saga);
    return saga;
  }

  public static SagaStep newStep(Saga saga, String id) {
    return newStep(saga, id, sagaStepBuilder -> {});
  }

  public static SagaStep newStep(
      Saga saga, String id, Consumer<SagaStep.SagaStepBuilder> customizer) {
    Instant now = Instant.now();
    SagaStep.SagaStepBuilder builder =
        SagaStep.builder()
            .id(id)
            .label(format("%s label", id))
            .createdAt(now)
            .updatedAt(now)
            .states(new ArrayList<>())
            .saga(saga)
            .attempt(1);

    customizer.accept(builder);

    SagaStep step = builder.build();
    saga.getSteps().add(step);
    return step;
  }
}
