package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class ResizeDcosServerGroupDescriptionValidatorSpec extends Specification {
  private static final DESCRIPTION = "resizeDcosServerGroupDescription"

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', null
  )

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> testCredentials
  }

  @Subject
  DescriptionValidator<ResizeDcosServerGroupDescription> validator = new ResizeDcosServerGroupDescriptionValidator(accountCredentialsProvider)

  void "validate should give errors when given an empty ResizeDcosServerGroupDescription"() {
    setup:
      def description = new ResizeDcosServerGroupDescription(credentials: null, serverGroupName: null, targetSize: null)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
      1 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      1 * errorsMock.rejectValue("targetSize", "${DESCRIPTION}.targetSize.invalid")
      0 * errorsMock._
  }

  void "validate should give errors when given an invalid DestroyDcosServerGroupDescription"() {
    setup:
      def description = new ResizeDcosServerGroupDescription(region: "region", credentials: new DcosCredentials(null, null, null, null, null), serverGroupName: 'test', targetSize: -1)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      1 * errorsMock.rejectValue("targetSize", "${DESCRIPTION}.targetSize.invalid")
      0 * errorsMock._
  }

  void "validate should give no errors when given an valid DestroyDcosServerGroupDescription"() {
    setup:
      def description = new ResizeDcosServerGroupDescription(region: "region", credentials: testCredentials, serverGroupName: 'test', targetSize: 0)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("region", "${DESCRIPTION}.region.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("serverGroupName", "${DESCRIPTION}.serverGroupName.empty")
      0 * errorsMock.rejectValue("targetSize", "${DESCRIPTION}.targetSize.invalid")
      0 * errorsMock._
  }
}
