package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.job

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job.RunDcosJobAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.model.DCOSAuthCredentials
import spock.lang.Specification
import spock.lang.Subject

class RunDcosJobAtomicOperationConverterSpec extends Specification {

    DCOS dcosClient = Mock(DCOS)

    DcosCredentials testCredentials = new DcosCredentials(
            'test', 'test', 'test', 'https://test.url.com', DCOSAuthCredentials.forUserAccount('user', 'pw')
    )

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials) >> dcosClient
    }

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials('test') >> testCredentials
    }

    @Subject
    AbstractAtomicOperationsCredentialsSupport atomicOperationConverter = new RunDcosJobAtomicOperationConverter(dcosClientProvider)

    void 'convertDescription should return a valid RunDcosJobDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                credentials: "test",
                general: [id: "testjob"]
        ]

        when:
        def description = atomicOperationConverter.convertDescription(input)

        then:
        noExceptionThrown()
        description != null
        description instanceof RunDcosJobDescription
    }

    void 'convertOperation should return a RunDcosJobAtomicOperation with a RunDcosJobDescription'() {
        given:
        atomicOperationConverter.accountCredentialsProvider = accountCredentialsProvider
        atomicOperationConverter.objectMapper = new ObjectMapper()
        def input = [
                account: "test",
                credentials: "test",
                general: [id: "testjob"]
        ]

        when:
        def atomicOperation = atomicOperationConverter.convertOperation(input)

        then:
        noExceptionThrown()
        atomicOperation != null
        atomicOperation instanceof RunDcosJobAtomicOperation
    }
}
