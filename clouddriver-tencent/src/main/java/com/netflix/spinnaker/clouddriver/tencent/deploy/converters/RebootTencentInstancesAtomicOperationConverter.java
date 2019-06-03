package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.RebootTencentInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.RebootTencentInstancesAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentInstancesDescription")
public class RebootTencentInstancesAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new RebootTencentInstancesAtomicOperation(convertDescription(input));
  }

  @Override
  public RebootTencentInstancesDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, RebootTencentInstancesDescription.class);
  }
}
