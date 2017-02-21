package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.instances

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instances.TerminateDcosInstancesDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class TerminateDcosInstanceDescriptionValidatorSpec extends Specification {
    private static final DESCRIPTION = "terminateDcosInstancesDescription"

    DcosCredentials testCredentials = new DcosCredentials(
            'test', 'test', 'test', 'https://test.url.com', 'user', 'pw'
    )

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials('test') >> testCredentials
    }

    @Subject
    DescriptionValidator<TerminateDcosInstancesDescription> validator = new TerminateDcosInstanceDescriptionValidator(accountCredentialsProvider)

    void "validate should give errors when given an empty TerminateDcosInstancesDescription"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: null, appId: null, hostId: null, taskIds: [], force: false, wipe: false)
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
            0 * errorsMock._
    }

    void "validate should give errors when given a TerminateDcosInstancesDescription with only an appId"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: new DcosCredentials(null, null, null, null, null, null),
                    appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: [], force: false, wipe: false)
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
            0 * errorsMock._
    }

    void "validate should give errors when given an invalid TerminateDcosInstancesDescription"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: ["TASK ONE", "TASK TWO"], force: false, wipe: false)
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
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesDescription with an appId, hostId, and no taskIds"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: [], force: false, wipe: false)
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
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesDescription with an appId, taskId, and no hostId"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                    appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: ["TASK ONE"], force: false, wipe: false)
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
            0 * errorsMock._
    }

    void "validate should give no errors when given a TerminateDcosInstancesDescription with taskId(s), no appId, and no hostId"() {
        setup:
            def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                    appId: null, hostId: null, taskIds: ["TASK ONE"], force: false, wipe: false)
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
            0 * errorsMock._
    }
}
