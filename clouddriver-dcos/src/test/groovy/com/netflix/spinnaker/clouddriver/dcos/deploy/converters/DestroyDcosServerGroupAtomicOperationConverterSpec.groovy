package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.converters.servergroup.DestroyDcosServerGroupAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.DestroyDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Specification
import spock.lang.Subject

class DestroyDcosServerGroupAtomicOperationConverterSpec extends Specification {

  DCOS dcosClient = Mock(DCOS)

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', 'user', 'pw'
  )

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> dcosClient
  }

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> testCredentials
  }

  @Subject
  AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new DestroyDcosServerGroupAtomicOperationConverter(dcosClientProvider)

  void 'convertDescription should return a valid DestroyDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      credentials: 'test',
      serverGroupName: 'api'
    ]

    when:
    AbstractDcosCredentialsDescription description = atomicOperationConverter.convertDescription(input)

    then:
    noExceptionThrown()
    description != null
    description instanceof DestroyDcosServerGroupDescription
  }

  void 'convertOperation should return a DestroyDcosServerGroupAtomicOperation with DestroyDcosServerGroupDescription'() {
    given:
    atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
    atomicOperationConverter.objectMapper = new ObjectMapper()
    Map input = [
      credentials: 'test',
      serverGroupName: 'api'
    ]

    when:
    AtomicOperation atomicOperation = atomicOperationConverter.convertOperation(input)

    then:
    noExceptionThrown()
    atomicOperation != null
    atomicOperation instanceof DestroyDcosServerGroupAtomicOperation
  }
}
