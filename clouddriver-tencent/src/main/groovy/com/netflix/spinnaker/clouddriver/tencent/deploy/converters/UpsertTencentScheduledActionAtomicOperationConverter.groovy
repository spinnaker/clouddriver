package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.ops.UpsertTencentScheduledActionAtomicOperation
import org.springframework.stereotype.Component

@Component("upsertTencentScheduledActionsDescription")
class UpsertTencentScheduledActionAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new UpsertTencentScheduledActionAtomicOperation(convertDescription(input))
  }

  @Override
  UpsertTencentScheduledActionDescription convertDescription(Map input) {
    TencentAtomicOperationConverterHelper.convertDescription(input, this, UpsertTencentScheduledActionDescription)
  }
}
