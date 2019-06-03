package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.RebootTencentInstancesDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.RebootTencentInstancesAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentInstancesDescription")
class RebootTencentInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new RebootTencentInstancesAtomicOperation(convertDescription(input))
  }

  @Override
  RebootTencentInstancesDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, RebootTencentInstancesDescription)
  }
}
