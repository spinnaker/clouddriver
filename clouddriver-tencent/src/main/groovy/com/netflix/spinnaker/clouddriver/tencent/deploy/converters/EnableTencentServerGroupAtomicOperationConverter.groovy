package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.EnableTencentServerGroupAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableTencentServerGroupDescription")
class EnableTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new EnableTencentServerGroupAtomicOperation(convertDescription(input))
  }

  EnableDisableTencentServerGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, EnableDisableTencentServerGroupDescription)
  }
}
