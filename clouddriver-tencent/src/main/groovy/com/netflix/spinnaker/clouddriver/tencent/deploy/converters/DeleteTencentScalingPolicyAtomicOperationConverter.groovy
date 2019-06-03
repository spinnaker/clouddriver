package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentScalingPolicyAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.DELETE_SCALING_POLICY)
@Component("deleteTencentScalingPolicyDescription")
class DeleteTencentScalingPolicyAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  DeleteTencentScalingPolicyDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, DeleteTencentScalingPolicyDescription)
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteTencentScalingPolicyAtomicOperation(convertDescription(input))
  }
}
