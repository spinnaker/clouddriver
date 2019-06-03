package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.DeleteTencentSecurityGroupAtomicOperation
import org.springframework.stereotype.Component


@TencentOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteTencentSecurityGroupDescription")
class DeleteTencentSecurityGroupAtomicOperationConverter  extends AbstractAtomicOperationsCredentialsSupport{
  AtomicOperation convertOperation(Map input) {
    new DeleteTencentSecurityGroupAtomicOperation(convertDescription(input))
  }

  DeleteTencentSecurityGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, DeleteTencentSecurityGroupDescription);
  }
}
