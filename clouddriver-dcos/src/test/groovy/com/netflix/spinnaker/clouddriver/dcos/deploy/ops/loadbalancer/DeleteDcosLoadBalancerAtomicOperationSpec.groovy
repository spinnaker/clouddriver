package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification
import spock.lang.Subject

class DeleteDcosLoadBalancerAtomicOperationSpec extends Specification {

  private static final ACCOUNT_NAME = "testAccount"
  private static final LOAD_BALANCER_NAME = "external"

  def credentials
  def dcosClientProviderMock
  def dcosClientMock
  def dcosDeploymentMonitorMock
  def appMock
  def taskMock

  def setup() {
    taskMock = Mock(Task)
    TaskRepository.threadLocalTask.set(taskMock)

    appMock = Mock(App)
    credentials = new DcosCredentials(ACCOUNT_NAME, "test", "test", "url", null)
    dcosDeploymentMonitorMock = Mock(DcosDeploymentMonitor)

    dcosClientMock = Mock(DCOS)
    dcosClientProviderMock = Stub(DcosClientProvider) {
      getDcosClient(credentials) >> dcosClientMock
    }
  }

  void "DeleteDcosLoadBalancerAtomicOperation should delete the load balancer for the given name if it exists"() {
    setup:
      appMock.id >> "/${ACCOUNT_NAME}/${LOAD_BALANCER_NAME}"

      def description = new DeleteDcosLoadBalancerAtomicOperationDescription(
              credentials: credentials,
              loadBalancerName: LOAD_BALANCER_NAME
      )

      @Subject def operation = new DeleteDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
              dcosDeploymentMonitorMock, description)

    when:
      operation.operate([])

    then:
      1 * dcosClientMock.maybeApp("/${ACCOUNT_NAME}/${LOAD_BALANCER_NAME}") >> Optional.of(appMock)
      1 * dcosClientMock.deleteApp(appMock.id)
      1 * dcosDeploymentMonitorMock.waitForAppDestroy(dcosClientMock, appMock, null, taskMock, "DESTROY_LOAD_BALANCER")
  }

  void "DeleteDcosLoadBalancerAtomicOperation should throw an exception when the given load balancer does not exist"() {
    setup:
      appMock.id >> "/${ACCOUNT_NAME}/${LOAD_BALANCER_NAME}"

      def description = new DeleteDcosLoadBalancerAtomicOperationDescription(
              credentials: credentials,
              loadBalancerName: LOAD_BALANCER_NAME
      )

      @Subject def operation = new DeleteDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
              dcosDeploymentMonitorMock, description)

    when:
      operation.operate([])

    then:
      1 * dcosClientMock.maybeApp("/${ACCOUNT_NAME}/${LOAD_BALANCER_NAME}") >> Optional.empty()
      thrown(DcosOperationException)
  }
}
