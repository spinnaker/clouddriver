package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentScheduledActionAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("deleteTencentScheduledActionDescription")
public class DeleteTencentScheduledActionAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteTencentScheduledActionAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteTencentScheduledActionDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, DeleteTencentScheduledActionDescription.class);
  }
}
