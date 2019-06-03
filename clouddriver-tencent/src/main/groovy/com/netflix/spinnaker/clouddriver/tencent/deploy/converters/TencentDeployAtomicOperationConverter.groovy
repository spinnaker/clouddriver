package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("tencentDeployDescription")
class TencentDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport{
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  TencentDeployDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, TencentDeployDescription)
  }
}
