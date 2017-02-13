package com.netflix.spinnaker.clouddriver.dcos.deploy.converters.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer.DeleteDcosLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationConverterSpec extends Specification {

  private static final ACCOUNT = "my-test-account"
  private static final LOAD_BALANCER_NAME = "external"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  @Subject
  DeleteDcosLoadBalancerAtomicOperationConverter converter

  @Shared
  DcosCredentials mockCredentials = Mock()

  def setupSpec() {
    converter = new DeleteDcosLoadBalancerAtomicOperationConverter(Mock(DcosClientProvider), Mock(DcosDeploymentMonitor))
    converter.setObjectMapper(mapper)
    converter.accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(ACCOUNT) >> mockCredentials
    }
  }

  void "deleteDcosLoadBalancerAtomicOperationConverter type returns DeleteDcosLoadBalancerAtomicOperationDescription and DeleteDcosLoadBalancerAtomicOperation"() {
    setup:
    def input = [region           : "dev",
                 loadBalancerName: LOAD_BALANCER_NAME,
                 credentials     : ACCOUNT]
    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof DeleteDcosLoadBalancerAtomicOperationDescription
    description.region == "dev"
    description.loadBalancerName == LOAD_BALANCER_NAME
    description.credentials == mockCredentials

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeleteDcosLoadBalancerAtomicOperation
  }
}
