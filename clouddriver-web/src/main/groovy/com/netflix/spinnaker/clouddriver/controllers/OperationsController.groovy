/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
class OperationsController {

  private final OperationsService operationsService

  @Autowired
  OperationsController(OperationsService operationsService) {
    this.operationsService = operationsService
  }
/**
   * @deprecated Use /{cloudProvider}/ops instead
   */
  @Deprecated
  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  OperationsService.StartOperationResult operations(
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(requestBody)
    return operationsService.start(atomicOperations, clientRequestId)
  }

  /**
   * @deprecated Use /{cloudProvider}/ops/{name} instead
   */
  @Deprecated
  @RequestMapping(value = "/ops/{name}", method = RequestMethod.POST)
  OperationsService.StartOperationResult operation(
    @PathVariable("name") String name,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations([[(name): requestBody]])
    return operationsService.start(atomicOperations, clientRequestId)
  }

  @RequestMapping(value = "/{cloudProvider}/ops", method = RequestMethod.POST)
  OperationsService.StartOperationResult cloudProviderOperations(
    @PathVariable("cloudProvider") String cloudProvider,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(cloudProvider, requestBody)
    return operationsService.start(atomicOperations, clientRequestId)
  }

  @RequestMapping(value = "/{cloudProvider}/ops/{name}", method = RequestMethod.POST)
  OperationsService.StartOperationResult cloudProviderOperation(
    @PathVariable("cloudProvider") String cloudProvider,
    @PathVariable("name") String name,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(cloudProvider, [[(name): requestBody]])
    return operationsService.start(atomicOperations, clientRequestId)
  }
}
