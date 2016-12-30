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

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.DestroyDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component
class DestroyDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  DestroyDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyDcosServerGroupAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  @Override
  DestroyDcosServerGroupDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DestroyDcosServerGroupDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
