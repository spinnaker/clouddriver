package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Subject

class DestroyDcosServerGroupAtomicOperationSpec extends BaseSpecification {
  private static final APPLICATION_NAME = 'api-test-v000'
  private static final REGION = 'default'

  DCOS dcosClient = Mock(DCOS)

  DcosCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> dcosClient
  }

  DestroyDcosServerGroupDescription description = new DestroyDcosServerGroupDescription(
    serverGroupName: APPLICATION_NAME, credentials: testCredentials, region: REGION
  )

  @Subject
  AtomicOperation atomicOperation = new DestroyDcosServerGroupAtomicOperation(dcosClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DestroyDcosServerGroupAtomicOperation should terminate the DCOS service successfully'() {
    when:
    atomicOperation.operate([])

    then:
    noExceptionThrown()
    1 * dcosClient.deleteApp(_) >> new Result()
  }
}
