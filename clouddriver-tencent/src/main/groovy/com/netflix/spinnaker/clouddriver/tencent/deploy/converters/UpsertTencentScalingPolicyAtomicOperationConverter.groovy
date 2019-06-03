package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentScalingPolicyAtomicOperation
import org.springframework.stereotype.Component

@TencentOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component("upsertTencentScalingPolicyDescription")
class UpsertTencentScalingPolicyAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  AtomicOperation convertOperation(Map input) {
    new UpsertTencentScalingPolicyAtomicOperation(convertDescription(input))
  }

  UpsertTencentScalingPolicyDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, UpsertTencentScalingPolicyDescription)
  }
}
