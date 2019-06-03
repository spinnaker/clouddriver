package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentSecurityGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentSecurityGroupDescription")
public class UpsertTencentSecurityGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new UpsertTencentSecurityGroupAtomicOperation(convertDescription(input));
  }

  public UpsertTencentSecurityGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, UpsertTencentSecurityGroupDescription.class);
  }
}
