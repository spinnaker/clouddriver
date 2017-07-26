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

package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer.DeleteDcosLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component
class DeleteDcosLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor

  @Autowired
  DeleteDcosLoadBalancerAtomicOperationConverter(DcosClientProvider dcosClientProvider,
                                                 DcosDeploymentMonitor deploymentMonitor) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
  }


  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteDcosLoadBalancerAtomicOperation(dcosClientProvider, deploymentMonitor, convertDescription(input))
  }

  @Override
  DeleteDcosLoadBalancerAtomicOperationDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, DeleteDcosLoadBalancerAtomicOperationDescription)
  }
}
