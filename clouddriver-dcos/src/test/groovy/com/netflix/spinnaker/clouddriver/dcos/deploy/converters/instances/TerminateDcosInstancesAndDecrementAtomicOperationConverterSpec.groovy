package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.instances

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instances.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instances.TerminateDcosInstancesAndDecrementAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import spock.lang.Specification
import spock.lang.Subject

class TerminateDcosInstancesAndDecrementAtomicOperationConverterSpec extends Specification {

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
    AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new TerminateDcosInstancesAndDecrementAtomicOperationConverter(dcosClientProvider)

    void 'convertDescription should return a valid TerminateDcosInstancesAndDecrementDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                credentials: "test",
                appId: "test/dev/app-stack-detail-v000",
                hostId: "192.168.0.0",
                taskIds: ["TASK ONE"],
                force: false
        ]

        when:
        def description = atomicOperationConverter.convertDescription(input)

        then:
        noExceptionThrown()
        description != null
        description instanceof TerminateDcosInstancesAndDecrementDescription
    }

    void 'convertOperation should return a TerminateDcosInstancesAndDecrementAtomicOperation with TerminateDcosInstancesAndDecrementDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                credentials: "test",
                appId: "test/dev/app-stack-detail-v000",
                hostId: "192.168.0.0",
                taskIds: ["TASK ONE"],
                force: false
        ]

        when:
        def atomicOperation = atomicOperationConverter.convertOperation(input)

        then:
        noExceptionThrown()
        atomicOperation != null
        atomicOperation instanceof TerminateDcosInstancesAndDecrementAtomicOperation
    }
}
