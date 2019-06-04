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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusOperation;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.UpdateTitusJobProcessesAtomicOperation;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@TitusOperation(AtomicOperations.UPDATE_JOB_PROCESSES)
@Component
public class UpdateTitusJobProcessesAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final TitusClientProvider titusClientProvider;
  private final ObjectMapper objectMapper;

  @Autowired
  UpdateTitusJobProcessesAtomicOperationConverter(
      TitusClientProvider titusClientProvider, ObjectMapper objectMapper) {
    this.titusClientProvider = titusClientProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpdateTitusJobProcessesAtomicOperation(
        titusClientProvider, convertDescription(input));
  }

  @Override
  public ServiceJobProcessesRequest convertDescription(Map input) {
    ServiceJobProcessesRequest converted =
        objectMapper.convertValue(input, ServiceJobProcessesRequest.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    return converted;
  }
}
