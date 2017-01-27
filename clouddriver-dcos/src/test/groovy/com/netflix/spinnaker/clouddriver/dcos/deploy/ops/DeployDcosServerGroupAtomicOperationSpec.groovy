package com.netflix.spinnaker.clouddriver.dcos.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.*
import spock.lang.Specification
import spock.lang.Subject

class DeployDcosServerGroupAtomicOperationSpec extends Specification {
  private static final APPLICATION_NAME = new DcosSpinnakerId('/test/region/api-test-detail-v000')

  DCOS mockDcosClient = Mock(DCOS)

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', 'user', 'pw'
  )

  DcosClientProvider mockDcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> mockDcosClient
  }

  DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
    application: APPLICATION_NAME.service.app, region: APPLICATION_NAME.region, credentials: testCredentials, stack: APPLICATION_NAME.service.stack,
    detail: APPLICATION_NAME.service.detail, instances: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
          container: new DeployDcosServerGroupDescription.Container(docker:
                  new DeployDcosServerGroupDescription.Docker(image: "test", forcePullImage: false, privileged: false,
                          portMappings: [], network: "BRIDGE")))

  App application = new App(id: APPLICATION_NAME.service.group, instances: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
    container: new Container(docker: new Docker(image: "test", forcePullImage: false, privileged: false, portMappings: [], network: "BRIDGE")),
    versionInfo: new VersionInfo(lastConfigChangeAt: null)
  )

  Result result = new Result()

  @Subject
  AtomicOperation atomicOperation = new DeployDcosServerGroupAtomicOperation(mockDcosClientProvider, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {
    given:
    mockDcosClient.getApps() >> new GetAppsResponse(apps: [application])

    when:
    DeploymentResult deploymentResult = atomicOperation.operate([])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains(APPLICATION_NAME.service.group)
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion.get(APPLICATION_NAME.namespace) == APPLICATION_NAME.service.group
  }
}
