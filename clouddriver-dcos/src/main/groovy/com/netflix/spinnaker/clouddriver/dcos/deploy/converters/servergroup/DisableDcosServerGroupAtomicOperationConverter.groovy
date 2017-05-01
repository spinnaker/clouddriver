package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.DisableDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component
class DisableDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  DisableDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DisableDcosServerGroupAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  @Override
  DisableDcosServerGroupDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, DisableDcosServerGroupDescription)
  }
}
