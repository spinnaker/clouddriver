package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateAndDecrementTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.TerminateAndDecrementTencentServerGroupAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component("terminateAndDecrementTencentServerGroupDescription")
class TerminateAndDecrementTencentServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  TerminateAndDecrementTencentServerGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, TerminateAndDecrementTencentServerGroupDescription)
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new TerminateAndDecrementTencentServerGroupAtomicOperation(convertDescription(input))
  }
}
