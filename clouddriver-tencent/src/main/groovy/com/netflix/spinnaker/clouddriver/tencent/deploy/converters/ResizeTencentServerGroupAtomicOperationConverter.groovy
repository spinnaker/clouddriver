package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.ResizeTencentServerGroupAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeTencentServerGroupDescription")
class ResizeTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new ResizeTencentServerGroupAtomicOperation(convertDescription(input))
  }

  ResizeTencentServerGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, ResizeTencentServerGroupDescription)
  }
}
