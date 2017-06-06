package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.DestroyDcosServerGroupAtomicOperation
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
    DcosAtomicOperationConverterHelper.convertDescription(input, this, DestroyDcosServerGroupDescription)
  }
}
