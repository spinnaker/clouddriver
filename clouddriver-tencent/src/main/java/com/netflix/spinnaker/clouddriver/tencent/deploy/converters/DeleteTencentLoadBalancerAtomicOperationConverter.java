package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentLoadBalancerAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentLoadBalancerDescription")
public class DeleteTencentLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new DeleteTencentLoadBalancerAtomicOperation(convertDescription(input));
  }

  public DeleteTencentLoadBalancerDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, DeleteTencentLoadBalancerDescription.class);
  }
}
