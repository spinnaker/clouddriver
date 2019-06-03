package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.ResizeTencentServerGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeTencentServerGroupDescription")
public class ResizeTencentServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new ResizeTencentServerGroupAtomicOperation(convertDescription(input));
  }

  public ResizeTencentServerGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, ResizeTencentServerGroupDescription.class);
  }
}
