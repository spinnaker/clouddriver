package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.ResizeDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Subject

class ResizeDcosServerGroupAtomicOperationConverterSpec extends BaseSpecification {

  DCOS dcosClient = Mock(DCOS)

  DcosCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> dcosClient
  }

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> testCredentials
  }

  @Subject
  AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new ResizeDcosServerGroupAtomicOperationConverter(dcosClientProvider)

  void 'convertDescription should return a valid ResizeDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      credentials: 'test',
      serverGroupName: 'api',
      targetSize: 1
    ]

    when:
    AbstractDcosCredentialsDescription description = atomicOperationConverter.convertDescription(input)

    then:
    noExceptionThrown()
    description != null
    description instanceof ResizeDcosServerGroupDescription
  }

  void 'convertOperation should return a ResizeDcosServerGroupAtomicOperation with ResizeDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      credentials: 'test',
      serverGroupName: 'api',
      targetSize: 1
    ]

    when:
    AtomicOperation atomicOperation = atomicOperationConverter.convertOperation(input)

    then:
    noExceptionThrown()
    atomicOperation != null
    atomicOperation instanceof ResizeDcosServerGroupAtomicOperation
  }
}
