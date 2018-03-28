/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TerminateTitusInstancesDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.TerminateTitusInstancesAtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@TitusOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component
class TerminateTitusInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final TitusClientProvider titusClientProvider

  @Autowired
  TerminateTitusInstancesAtomicOperationConverter(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new TerminateTitusInstancesAtomicOperation(titusClientProvider, convertDescription(input))
  }

  @Override
  TerminateTitusInstancesDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, TerminateTitusInstancesDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }

}
