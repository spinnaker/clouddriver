package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.EnableTencentServerGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableTencentServerGroupDescription")
public class EnableTencentServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new EnableTencentServerGroupAtomicOperation(convertDescription(input));
  }

  public EnableDisableTencentServerGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, EnableDisableTencentServerGroupDescription.class);
  }
}
