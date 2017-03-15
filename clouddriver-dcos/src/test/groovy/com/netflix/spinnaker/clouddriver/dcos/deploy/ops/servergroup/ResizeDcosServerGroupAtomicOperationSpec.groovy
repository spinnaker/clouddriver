package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.model.DCOSAuthCredentials
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Specification
import spock.lang.Subject

class ResizeDcosServerGroupAtomicOperationSpec extends Specification {
  private static final APPLICATION_NAME = 'api-test-v000'

  DCOS dcosClient = Mock(DCOS)

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', DCOSAuthCredentials.forUserAccount('user', 'pw')
  )

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> dcosClient
  }

  ResizeDcosServerGroupDescription description = new ResizeDcosServerGroupDescription(
    region: "default", serverGroupName: APPLICATION_NAME, credentials: testCredentials, targetSize: 2
  )

  @Subject
  AtomicOperation atomicOperation = new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'ResizeDcosServerGroupAtomicOperation should resize the DCOS service successfully'() {
    when:
    atomicOperation.operate([])

    then:
    noExceptionThrown()
    1 * dcosClient.maybeApp(_) >> Optional.of(new App())
    1 * dcosClient.updateApp(_, _, _) >> new Result()
  }
}
