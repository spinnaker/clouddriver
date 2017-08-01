/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.ResizeDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component
class ResizeDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  ResizeDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  @Override
  ResizeDcosServerGroupDescription convertDescription(Map input) {
    ResizeDcosServerGroupDescription desc = DcosAtomicOperationConverterHelper.convertDescription(input, this, ResizeDcosServerGroupDescription)
    desc.targetSize = input?.targetSize ?: input.capacity.desired
    desc
  }
}
