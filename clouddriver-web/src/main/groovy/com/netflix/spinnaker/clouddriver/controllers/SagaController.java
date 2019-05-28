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
package com.netflix.spinnaker.clouddriver.controllers;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.repository.ListStateCriteria;
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/sagas", produces = "application/json")
@RestController
@Slf4j
public class SagaController {

  private final SagaRepository sagaRepository;

  @Autowired
  public SagaController(SagaRepository sagaRepository) {
    this.sagaRepository = sagaRepository;
  }

  @GetMapping()
  SagaRepository.ListResult<Saga> list() {
    return sagaRepository.list(ListStateCriteria.none());
  }

  @GetMapping("/{id}")
  Saga get(@PathVariable String id) {
    return Optional.ofNullable(sagaRepository.get(id))
        .orElseThrow(() -> new NotFoundException(format("Saga '%s' not found", id)));
  }
}
