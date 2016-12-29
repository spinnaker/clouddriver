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

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.securitygroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.securitygroup.DeleteDcosSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.securitygroup.DeleteDcosSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component
class DeleteDcosSecurityGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteDcosSecurityGroupAtomicOperation(convertDescription(input))
  }

  @Override
  DeleteDcosSecurityGroupDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, DeleteDcosSecurityGroupDescription)
  }
}
