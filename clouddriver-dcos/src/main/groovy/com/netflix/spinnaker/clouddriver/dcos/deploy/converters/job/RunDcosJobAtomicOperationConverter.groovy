package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.job

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.DcosAtomicOperationConverterHelper
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job.RunDcosJobAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@DcosOperation(AtomicOperations.RUN_JOB)
@Component
class RunDcosJobAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  private final DcosClientProvider dcosClientProvider

  @Autowired
  RunDcosJobAtomicOperationConverter(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  AtomicOperation convertOperation(Map input) {
    new RunDcosJobAtomicOperation(dcosClientProvider, convertDescription(input))
  }

  RunDcosJobDescription convertDescription(Map input) {
    DcosAtomicOperationConverterHelper.convertDescription(input, this, RunDcosJobDescription)
  }
}

