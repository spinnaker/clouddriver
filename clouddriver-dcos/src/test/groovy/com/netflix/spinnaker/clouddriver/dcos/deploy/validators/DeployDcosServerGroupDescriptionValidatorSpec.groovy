package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class DeployDcosServerGroupDescriptionValidatorSpec extends Specification {
  private static final DESCRIPTION = "deployDcosServerGroupDescription"

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', 'user', 'pw'
  )

  AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider) {
    getCredentials('test') >> testCredentials
  }

  @Subject
  DescriptionValidator<DeployDcosServerGroupDescription> validator = new DeployDcosServerGroupDescriptionValidator(accountCredentialsProvider)

  void "validate should give errors when given an empty DeployDcosServerGroupDescription"() {
    setup:
      def description = new DeployDcosServerGroupDescription(credentials: null, application: null, instances: -1,
        cpus: -1, mem: -1, disk: -1, gpus: -1, container: null)
      def errorsMock = Mock(Errors)
    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
      1 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
      1 * errorsMock.rejectValue("instances", "${DESCRIPTION}.instances.invalid")
      1 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
      1 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
      1 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
      1 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
      1 * errorsMock.rejectValue("container", "${DESCRIPTION}.container.empty")
      0 * errorsMock._
  }

  void "validate should give errors when given an invalid DeployDcosServerGroupDescription"() {
    setup:
    def description = new DeployDcosServerGroupDescription(credentials: new DcosCredentials(null, null, null, null, null, null),
            application: 'test', instances: 1, cpus: 1, mem: 512, disk: 0, gpus: 0,
      container: new DeployDcosServerGroupDescription.Container(docker: null))
    def errorsMock = Mock(Errors)
    when:
    validator.validate([], description, errorsMock)
    then:
    0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.invalid")
    0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
    0 * errorsMock.rejectValue("instances", "${DESCRIPTION}.instances.invalid")
    0 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    0 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    0 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
    0 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
    0 * errorsMock.rejectValue("container", "${DESCRIPTION}.container.empty")
    0 * errorsMock._
  }

  void "validate should give no errors when given an valid DeployDcosServerGroupDescription"() {
    setup:
    def description = new DeployDcosServerGroupDescription(credentials: testCredentials, application: 'test',
      instances: 1, cpus: 1, mem: 512, disk: 0, gpus: 0, container: new DeployDcosServerGroupDescription.Container(docker: null))
    def errorsMock = Mock(Errors)
    when:
    validator.validate([], description, errorsMock)
    then:
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock.rejectValue("application", "${DESCRIPTION}.application.empty")
      0 * errorsMock.rejectValue("instances", "${DESCRIPTION}.instances.invalid")
      0 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
      0 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
      0 * errorsMock.rejectValue("disk", "${DESCRIPTION}.disk.invalid")
      0 * errorsMock.rejectValue("gpus", "${DESCRIPTION}.gpus.invalid")
      0 * errorsMock.rejectValue("container", "${DESCRIPTION}.container.empty")
      0 * errorsMock._
  }
}
