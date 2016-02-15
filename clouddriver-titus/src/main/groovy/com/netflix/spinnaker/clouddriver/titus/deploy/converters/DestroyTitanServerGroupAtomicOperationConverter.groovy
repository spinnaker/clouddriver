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
import com.netflix.spinnaker.clouddriver.titus.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitanOperation
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitanServerGroupDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.DestroyTitanServerGroupAtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@TitanOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component
class DestroyTitanServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final TitanClientProvider titanClientProvider

  @Autowired
  DestroyTitanServerGroupAtomicOperationConverter(TitanClientProvider titanClientProvider) {
    this.titanClientProvider = titanClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyTitanServerGroupAtomicOperation(titanClientProvider, convertDescription(input))
  }

  @Override
  DestroyTitanServerGroupDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DestroyTitanServerGroupDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
