package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationDescriptionValidatorSpec extends Specification {

  private static final DESCRIPTION = "deleteDcosLoadBalancerAtomicOperationDescription"
  private static final ACCOUNT = "my-test-account"

  @Shared
  @Subject
  DeleteDcosLoadBalancerAtomicOperationDescriptionValidator validator

  @Shared
  DcosCredentials testCredentials = new DcosCredentials(
          ACCOUNT, 'test', 'test', 'url', 'user', 'pass'
  )

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(ACCOUNT) >> testCredentials
    }
    validator = new DeleteDcosLoadBalancerAtomicOperationDescriptionValidator(accountCredentialsProvider)
  }

  void "successfully validates when no fields are missing or invalid"() {
    setup:
    def description = new DeleteDcosLoadBalancerAtomicOperationDescription().with {
      loadBalancerName = "lb"
      credentials = testCredentials
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    0 * errorsMock._
  }

  void "reports an error when no credentials are present"() {
    setup:
    def description = new DeleteDcosLoadBalancerAtomicOperationDescription().with {
      loadBalancerName = "lb"
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    0 * errorsMock._
  }

  void "reports an error when the loadBalancerName is not provided"() {
    setup:
    def description = new DeleteDcosLoadBalancerAtomicOperationDescription().with {
      credentials = testCredentials
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("loadBalancerName", "${DESCRIPTION}.loadBalancerName.empty")
    0 * errorsMock._
  }
}