package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.job

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.model.DCOSAuthCredentials
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class RunDcosJobValidatorSpec extends Specification {
    private static final DESCRIPTION = "runDcosJobDescription"

    DcosCredentials testCredentials = new DcosCredentials(
            'test', 'test', 'test', 'https://test.url.com', DCOSAuthCredentials.forUserAccount('user', 'pw')
    )

    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
        getCredentials('test') >> testCredentials
    }

    @Subject
    DescriptionValidator<RunDcosJobDescription> validator = new RunDcosJobDescriptionValidator(accountCredentialsProvider)

    void "validate should give errors when given an empty RunDcosJobDescription"() {
        setup:
            def description = new RunDcosJobDescription(credentials: null, general: null)
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            1 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.empty")
            0 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.invalid")
            0 * errorsMock._
    }

    void "validate should give errors when given a RunDcosJobDescription with invalid credentials and an invalid id"() {
        setup:
            def description = new RunDcosJobDescription(credentials: new DcosCredentials(null, null, null, null, null),
                    general: new RunDcosJobDescription.GeneralSettings().with {
                        id = '/iNv.aLiD-'
                        it
                    })
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.empty")
            1 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.invalid")
            0 * errorsMock._
    }

    void "validate should give no errors when given a RunDcosJobDescription with a valid credential and id"() {
        setup:
            def description = new RunDcosJobDescription(credentials: testCredentials,
                    general: new RunDcosJobDescription.GeneralSettings().with {
                        id = 'testjob'
                        it
                    })
            def errorsMock = Mock(Errors)
        when:
            validator.validate([], description, errorsMock)
        then:
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
            0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
            0 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.empty")
            0 * errorsMock.rejectValue("general.id", "${DESCRIPTION}.general.id.invalid")
            0 * errorsMock._
    }
}
