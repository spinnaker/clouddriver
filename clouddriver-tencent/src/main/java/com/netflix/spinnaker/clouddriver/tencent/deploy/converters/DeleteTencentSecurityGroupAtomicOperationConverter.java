package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentSecurityGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteTencentSecurityGroupDescription")
public class DeleteTencentSecurityGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  public AtomicOperation convertOperation(Map input) {
    return new DeleteTencentSecurityGroupAtomicOperation(convertDescription(input));
  }

  public DeleteTencentSecurityGroupDescription convertDescription(Map input) {
    return TencentAtomicOperationConverterHelper.convertDescription(
        input, this, DeleteTencentSecurityGroupDescription.class);
  }
}
