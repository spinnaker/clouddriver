package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instances

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance.TerminateDcosInstancesAndDecrementAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component
class TerminateDcosInstancesAndDecrementAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  TerminateDcosInstancesAndDecrementAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new TerminateDcosInstancesAndDecrementAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  @Override
  TerminateDcosInstancesAndDecrementDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, TerminateDcosInstancesAndDecrementDescription)
  }
}
