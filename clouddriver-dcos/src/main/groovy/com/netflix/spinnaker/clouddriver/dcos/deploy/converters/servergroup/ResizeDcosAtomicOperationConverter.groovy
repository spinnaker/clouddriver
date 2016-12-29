/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.ResizeDcosAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component
class ResizeDcosAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new ResizeDcosAtomicOperation(convertDescription(input))
  }

  ResizeDcosAtomicOperationDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, ResizeDcosAtomicOperationDescription)
  }
}

