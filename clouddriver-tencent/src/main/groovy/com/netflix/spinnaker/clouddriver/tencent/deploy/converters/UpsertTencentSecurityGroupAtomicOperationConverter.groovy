package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.TencentOperation
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentSecurityGroupAtomicOperation
import org.springframework.stereotype.Component


@TencentOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentSecurityGroupDescription")
class UpsertTencentSecurityGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport{
  AtomicOperation convertOperation(Map input) {
    new UpsertTencentSecurityGroupAtomicOperation(convertDescription(input))
  }

  UpsertTencentSecurityGroupDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, UpsertTencentSecurityGroupDescription)
  }
}
