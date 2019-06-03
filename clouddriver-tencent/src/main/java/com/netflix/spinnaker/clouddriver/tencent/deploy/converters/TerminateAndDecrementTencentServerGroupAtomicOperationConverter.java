package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateAndDecrementTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.TerminateAndDecrementTencentServerGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component("terminateAndDecrementTencentServerGroupDescription")
public class TerminateAndDecrementTencentServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public TerminateAndDecrementTencentServerGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, TerminateAndDecrementTencentServerGroupDescription.class);
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new TerminateAndDecrementTencentServerGroupAtomicOperation(convertDescription(input));
  }
}
