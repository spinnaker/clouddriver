package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.DestroyDcosServerGroupAtomicOperation
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
    def converted = objectMapper.convertValue(input, DestroyDcosServerGroupDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
