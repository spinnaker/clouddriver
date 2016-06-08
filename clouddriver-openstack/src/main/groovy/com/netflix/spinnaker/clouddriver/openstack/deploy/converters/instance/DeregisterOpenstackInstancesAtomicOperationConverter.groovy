/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.converters.instance

import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.converters.OpenstackAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance.DeregisterOpenstackInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@OpenstackOperation(AtomicOperations.DEREGISTER_INSTANCES_FROM_LOAD_BALANCER)
@Component("deregisterOpenstackInstancesDescription")
class DeregisterOpenstackInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new DeregisterOpenstackInstancesAtomicOperation(convertDescription(input))
  }

  @Override
  OpenstackInstancesRegistrationDescription convertDescription(Map input) {
    OpenstackAtomicOperationConverterHelper.convertDescription(input, this, OpenstackInstancesRegistrationDescription)
  }
}
