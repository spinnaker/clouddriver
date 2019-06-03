package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentLoadBalancerAtomicOperation
import org.springframework.stereotype.Component


@TencentOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentLoadBalancerDescription")
class DeleteTencentLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport{
  AtomicOperation convertOperation(Map input) {
    new DeleteTencentLoadBalancerAtomicOperation(convertDescription(input))
  }

  DeleteTencentLoadBalancerDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, DeleteTencentLoadBalancerDescription)
  }
}
