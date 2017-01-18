package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.DeployDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
class DeployDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  DeployDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  AtomicOperation convertOperation(Map input) {
    new DeployDcosServerGroupAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  DeployDcosServerGroupDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DeployDcosServerGroupDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
