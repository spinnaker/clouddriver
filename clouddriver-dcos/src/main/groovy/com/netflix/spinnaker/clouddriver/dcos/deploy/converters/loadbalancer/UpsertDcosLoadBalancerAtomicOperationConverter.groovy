package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer.UpsertDcosLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
class UpsertDcosLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final DcosConfigurationProperties dcosConfigurationProperties

  @Autowired
  UpsertDcosLoadBalancerAtomicOperationConverter(DcosClientProvider dcosClientProvider,
                                                 DcosDeploymentMonitor deploymentMonitor,
                                                 DcosConfigurationProperties dcosConfigurationProperties) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
    this.dcosConfigurationProperties = dcosConfigurationProperties
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new UpsertDcosLoadBalancerAtomicOperation(dcosClientProvider, deploymentMonitor, dcosConfigurationProperties, convertDescription(input))
  }

  @Override
  UpsertDcosLoadBalancerAtomicOperationDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, UpsertDcosLoadBalancerAtomicOperationDescription)
  }
}
