package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.CloneDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.CloneDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneDcosServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final DcosClientProvider dcosClientProvider
  private final DeployDcosServerGroupDescriptionToAppMapper dcosServerGroupDescriptionToAppMapper

  @Autowired
  CloneDcosServerGroupAtomicOperationConverter(DcosClientProvider dcosClientProvider, DeployDcosServerGroupDescriptionToAppMapper dcosServerGroupDescriptionToAppMapper) {
    this.dcosClientProvider = dcosClientProvider
    this.dcosServerGroupDescriptionToAppMapper = dcosServerGroupDescriptionToAppMapper
  }

  AtomicOperation convertOperation(Map input) {
    new CloneDcosServerGroupAtomicOperation(dcosClientProvider, dcosServerGroupDescriptionToAppMapper, convertDescription(input))
  }

  CloneDcosServerGroupDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, CloneDcosServerGroupDescription)
  }
}

