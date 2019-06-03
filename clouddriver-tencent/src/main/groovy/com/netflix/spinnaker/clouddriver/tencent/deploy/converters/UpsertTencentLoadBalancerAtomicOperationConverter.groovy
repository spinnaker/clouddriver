package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentLoadBalancerAtomicOperation
import org.springframework.stereotype.Component


@TencentOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertTencentLoadBalancerDescription")
class UpsertTencentLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport{
  AtomicOperation convertOperation(Map input) {
    new UpsertTencentLoadBalancerAtomicOperation(convertDescription(input))
  }

  UpsertTencentLoadBalancerDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, UpsertTencentLoadBalancerDescription)
  }
}
