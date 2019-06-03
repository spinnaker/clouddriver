package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentScheduledActionAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("upsertTencentScheduledActionsDescription")
public class UpsertTencentScheduledActionAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpsertTencentScheduledActionAtomicOperation(convertDescription(input));
  }

  @Override
  public UpsertTencentScheduledActionDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, UpsertTencentScheduledActionDescription.class);
  }
}
