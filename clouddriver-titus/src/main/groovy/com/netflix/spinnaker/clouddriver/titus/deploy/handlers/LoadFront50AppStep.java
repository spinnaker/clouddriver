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
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaEventHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.Front50AppLoaded;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCreated;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadFront50AppStep implements SagaEventHandler<TitusDeployCreated> {
  private static final Logger log = LoggerFactory.getLogger(LoadFront50AppStep.class);

  private final Front50Service front50Service;
  private final ObjectMapper objectMapper;

  public LoadFront50AppStep(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  @NotNull
  @Override
  public List<SagaEvent> apply(@NotNull TitusDeployCreated event, @NotNull Saga saga) {
    final String application = event.getDescription().getApplication();

    try {
      Map response = front50Service.getApplication(application);
      try {
        return Collections.singletonList(
            new Front50AppLoaded(
                saga.getName(),
                saga.getId(),
                Optional.ofNullable(response)
                    .map(
                        it ->
                            objectMapper.convertValue(
                                it, TitusDeployHandler.Front50Application.class))
                    .orElse(null),
                event.getDescription()));
      } catch (IllegalArgumentException e) {
        log.error("Failed to convert front50 application to internal model", e);
        throw new TitusException(e);
      }
    } catch (Exception e) {
      log.error("Failed to load front50 application attributes for {}", application, e);
      throw new TitusException(e);
    }
  }

  @Override
  public void compensate(@NotNull TitusDeployCreated event, @NotNull Saga saga) {}

  @Override
  public void finalize(@NotNull TitusDeployCreated event, @NotNull Saga saga) {}
}
