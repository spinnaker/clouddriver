package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription
import org.springframework.stereotype.Component
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DestroyTencentServerGroupAtomicOperation

@TencentOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyTencentServerGroupDescription")
class DestroyTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DestroyTencentServerGroupAtomicOperation(convertDescription(input))
  }

  DestroyTencentServerGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, DestroyTencentServerGroupDescription)
  }
}
