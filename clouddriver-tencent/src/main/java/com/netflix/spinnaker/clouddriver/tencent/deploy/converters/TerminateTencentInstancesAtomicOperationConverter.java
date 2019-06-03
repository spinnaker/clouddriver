package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.TerminateTencentInstancesAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentInstancesDescription")
public class TerminateTencentInstancesAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public TerminateTencentInstancesDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, TerminateTencentInstancesDescription.class);
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new TerminateTencentInstancesAtomicOperation(convertDescription(input));
  }
}
