package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class DisableDcosServerGroupDescriptionValidatorSpec extends Specification {
  private static final def DESCRIPTION = "disableDcosServerGroupDescription"
  private static final def INVALID_MARATHON_PART = "-iNv.aLid-"

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', Config.builder().withCredentials(DCOSAuthCredentials.forUserAccount('user', 'pw')).build()
  )

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> testCredentials
  }

  @Subject
  DescriptionValidator<DisableDcosServerGroupDescription> validator = new DisableDcosServerGroupDescriptionValidator(accountCredentialsProvider)

  void "validate should give errors when given an empty DestroyDcosServerGroupDescription"() {
    setup:
      def description = new DisableDcosServerGroupDescription(region: null, credentials: null, serverGroupName: null)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
      1 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }

  void "validate should give errors when given an invalid DestroyDcosServerGroupDescription"() {
    setup:
      def description = new DisableDcosServerGroupDescription(region: INVALID_MARATHON_PART, credentials: new DcosCredentials(null, null, null, null, null), serverGroupName: INVALID_MARATHON_PART)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      1 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }

  void "validate should give no errors when given an valid DestroyDcosServerGroupDescription"() {
    setup:
    def description = new DisableDcosServerGroupDescription(region: "region", credentials: testCredentials, serverGroupName: 'test')
    def errorsMock = Mock(Errors)
    when:
    validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.invalid")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.invalid")
      0 * errorsMock._
  }
}
