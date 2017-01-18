package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.ResizeDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component
class ResizeDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider

  @Autowired
  ResizeDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  @Override
  ResizeDcosServerGroupDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, ResizeDcosServerGroupDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
