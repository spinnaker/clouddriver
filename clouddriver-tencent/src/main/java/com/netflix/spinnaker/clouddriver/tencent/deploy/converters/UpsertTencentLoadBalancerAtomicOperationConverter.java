package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentLoadBalancerAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertTencentLoadBalancerDescription")
public class UpsertTencentLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new UpsertTencentLoadBalancerAtomicOperation(convertDescription(input));
  }

  public UpsertTencentLoadBalancerDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, UpsertTencentLoadBalancerDescription.class);
  }
}
