package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationDescriptionValidatorSpec extends BaseSpecification {

  private static final DESCRIPTION = "deleteDcosLoadBalancerAtomicOperationDescription"

  @Shared
  @Subject
  DeleteDcosLoadBalancerAtomicOperationDescriptionValidator validator

  @Shared
  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(testCredentials.name) >> testCredentials
    }
    validator = new DeleteDcosLoadBalancerAtomicOperationDescriptionValidator(accountCredentialsProvider)
  }

  void "successfully validates when no fields are missing or invalid"() {
    setup:
    def description = new DeleteDcosLoadBalancerAtomicOperationDescription().with {
      loadBalancerName = "lbname"
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
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
      loadBalancerName = "-iNv.aLid-"
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    1 * errorsMock.rejectValue("dcosCluster", "${DESCRIPTION}.dcosCluster.empty")
    1 * errorsMock.rejectValue("loadBalancerName", "${DESCRIPTION}.loadBalancerName.invalid")
    0 * errorsMock._
  }

  void "reports an error when the loadBalancerName is not provided"() {
    setup:
    def description = new DeleteDcosLoadBalancerAtomicOperationDescription().with {
      credentials = testCredentials
      dcosCluster = DEFAULT_REGION
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
