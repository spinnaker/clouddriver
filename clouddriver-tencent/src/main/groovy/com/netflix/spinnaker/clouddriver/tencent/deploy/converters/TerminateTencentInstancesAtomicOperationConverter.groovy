package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.TerminateTencentInstancesAtomicOperation
import org.springframework.stereotype.Component


@TencentOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentInstancesDescription")
class TerminateTencentInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  TerminateTencentInstancesDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, TerminateTencentInstancesDescription)
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new TerminateTencentInstancesAtomicOperation(convertDescription(input))
  }
}
