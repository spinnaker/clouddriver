package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentScheduledActionAtomicOperation
import org.springframework.stereotype.Component

@Component("deleteTencentScheduledActionDescription")
class DeleteTencentScheduledActionAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteTencentScheduledActionAtomicOperation(convertDescription(input))
  }

  @Override
  DeleteTencentScheduledActionDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, DeleteTencentScheduledActionDescription)
  }
}
