package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription.PortRange

class UpsertDcosLoadBalancerAtomicOperationDescriptionValidatorSpec extends BaseSpecification {
  private static final DESCRIPTION = "upsertDcosLoadBalancerAtomicOperationDescription"
  private static final ACCOUNT = "my-test-account"

  @Shared
  @Subject
  UpsertDcosLoadBalancerAtomicOperationDescriptionValidator validator

  @Shared
  DcosCredentials testCredentials = defaultCredentialsBuilder().name(ACCOUNT).build()

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(ACCOUNT) >> testCredentials
    }
    validator = new UpsertDcosLoadBalancerAtomicOperationDescriptionValidator(accountCredentialsProvider)
  }

  void "successfully validates when no fields are missing or invalid"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    0 * errorsMock._
  }

  void "reports an error when the load balance name is invalid"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "-iNv.aLid-"
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("name", "${DESCRIPTION}.name.invalid")
    0 * errorsMock._
  }

  void "reports an error when no credentials are present"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("credentials", "${DESCRIPTION}.credentials.empty")
    0 * errorsMock._
  }

  void "reports an error when the name is not provided"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("name", "${DESCRIPTION}.name.empty")
    0 * errorsMock._
  }

  void "reports errors for invalid resource capacities"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      cpus = -1
      mem = -1
      instances = -1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("cpus", "${DESCRIPTION}.cpus.invalid")
    1 * errorsMock.rejectValue("mem", "${DESCRIPTION}.mem.invalid")
    1 * errorsMock.rejectValue("instances", "${DESCRIPTION}.instances.invalid")
    0 * errorsMock._
  }

  void "reports an error when acceptedResourceRoles contains a null value"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = [null]
      portRange = new PortRange(protocol: "tcp", minPort: 10000, maxPort: 10100)
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("acceptedResourceRoles", "${DESCRIPTION}.acceptedResourceRoles.invalid (Must not contain null or empty values)")
    0 * errorsMock._
  }

  void "reports an error if portRange is not provided"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.empty")
    0 * errorsMock._
  }

  void "reports an error if portRange has invalid port definitions"() {
    setup:
    def description = new UpsertDcosLoadBalancerAtomicOperationDescription().with {
      name = "lb"
      credentials = testCredentials
      cpus = 1
      mem = 256
      instances = 1
      bindHttpHttps = true
      acceptedResourceRoles = ["resource"]
      portRange = new PortRange(protocol: "", minPort: 9999, maxPort: 25)

      it
    }

    def errorsMock = Mock(Errors)

    when:
    validator.validate([], description, errorsMock)

    then:
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.protocol.invalid")
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.minPort.invalid (minPort < 10000)")
    1 * errorsMock.rejectValue("portRange", "${DESCRIPTION}.portRange.invalid (maxPort < minPort)")

    0 * errorsMock._
  }
}
