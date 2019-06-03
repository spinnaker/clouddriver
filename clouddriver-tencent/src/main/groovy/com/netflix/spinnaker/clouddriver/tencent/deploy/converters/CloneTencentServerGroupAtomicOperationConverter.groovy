package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.CloneTencentServerGroupAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component("cloneTencentServerGroupDescription")
class CloneTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  AtomicOperation convertOperation(Map input) {
    new CloneTencentServerGroupAtomicOperation(convertDescription(input))
  }

  TencentDeployDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, TencentDeployDescription)
  }
}
