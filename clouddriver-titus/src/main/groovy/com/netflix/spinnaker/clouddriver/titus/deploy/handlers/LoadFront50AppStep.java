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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.saga.DefaultStepResult;
import com.netflix.spinnaker.clouddriver.saga.SagaStepFunction;
import com.netflix.spinnaker.clouddriver.saga.SingleValueStepResult;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoadFront50AppStep implements SagaStepFunction {

  private final Front50Service front50Service;
  private final ObjectMapper objectMapper;

  public LoadFront50AppStep(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @Override
  public StepResult apply(SagaState sagaState) {
    final String application =
        sagaState.getRequired("description", TitusDeployDescription.class).getApplication();

    try {
      Map response = front50Service.getApplication(application);
      try {
        return new SingleValueStepResult(
            "front50Application",
            objectMapper.convertValue(response, TitusDeployHandler.Front50Application.class));
      } catch (IllegalArgumentException e) {
        log.error("Failed to convert front50 application to internal model", e);
        return new DefaultStepResult(new TitusException(e));
      }
    } catch (Exception e) {
      log.error("Failed to load front50 application attributes for {}", application, e);
      return new DefaultStepResult(new TitusException(e));
    }
  }
}
