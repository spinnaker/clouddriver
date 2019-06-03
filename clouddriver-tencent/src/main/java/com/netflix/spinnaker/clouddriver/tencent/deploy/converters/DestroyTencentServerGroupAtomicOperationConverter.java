package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DestroyTencentServerGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyTencentServerGroupDescription")
public class DestroyTencentServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new DestroyTencentServerGroupAtomicOperation(convertDescription(input));
  }

  public DestroyTencentServerGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, DestroyTencentServerGroupDescription.class);
  }
}
