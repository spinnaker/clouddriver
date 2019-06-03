package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DisableTencentServerGroupAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("disableTencentServerGroupDescription")
class DisableTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  AtomicOperation convertOperation(Map input) {
    new DisableTencentServerGroupAtomicOperation(convertDescription(input))
  }

  EnableDisableTencentServerGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, EnableDisableTencentServerGroupDescription)
  }
}
