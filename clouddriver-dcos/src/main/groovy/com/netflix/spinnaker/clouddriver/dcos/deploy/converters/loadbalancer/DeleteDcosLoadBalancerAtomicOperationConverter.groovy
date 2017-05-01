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
