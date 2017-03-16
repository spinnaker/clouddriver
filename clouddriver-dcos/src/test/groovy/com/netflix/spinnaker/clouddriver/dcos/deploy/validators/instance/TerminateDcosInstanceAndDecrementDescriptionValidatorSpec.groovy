package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.model.DCOSAuthCredentials
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class TerminateDcosInstanceAndDecrementDescriptionValidatorSpec extends Specification {
    private static final DESCRIPTION = "terminateDcosInstancesAndDecrementDescription"

    DcosCredentials testCredentials = new DcosCredentials(
            'test', 'test', 'test', 'https://test.url.com', DCOSAuthCredentials.forUserAccount('user', 'pw')
    )

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials('test') >> testCredentials
    }

    @Subject
    DescriptionValidator<TerminateDcosInstancesAndDecrementDescription> validator = new TerminateDcosInstanceAndDecrementDescriptionValidator(accountCredentialsProvider)

    void "validate should give errors when given an empty TerminateDcosInstancesAndDecrementDescription"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: null, appId: null, hostId: null, taskIds: [], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            1 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            0 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }

    void "validate should give errors when given a TerminateDcosInstancesAndDecrementDescription with only an appId"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: new DcosCredentials(null, null, null, null, null),
                    appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: [], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            1 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            1 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }

    void "validate should give errors when given an invalid TerminateDcosInstancesAndDecrementDescription"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: ["TASK ONE", "TASK TWO"], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            1 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            1 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            0 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesAndDecrementDescription with an appId, hostId, and no taskIds"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: [], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            0 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesAndDecrementDescription with an appId, taskId, and no hostId"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: ["TASK ONE"], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            0 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesAndDecrementDescription with taskId(s), no appId, and no hostId"() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                    appId: null, hostId: null, taskIds: ["TASK ONE"], force: false)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.empty")
            0 * errorsMock.rejectValue("hostId|taskIds", "${DESCRIPTION}.hostId|taskIds.invalid")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.empty")
            0 * errorsMock.rejectValue("taskIds", "${DESCRIPTION}.taskIds.invalid")
            0 * errorsMock.rejectValue("appId", "${DESCRIPTION}.appId.invalid")
            0 * errorsMock._
    }
}
