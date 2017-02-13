package com.netflix.spinnaker.clouddriver.dcos.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup.DeployDcosServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.*
import spock.lang.Specification
import spock.lang.Subject

class DeployDcosServerGroupAtomicOperationSpec extends Specification {
  private static final APPLICATION_NAME = new DcosSpinnakerId('/test/region/api-test-detail-v000')

  DCOS mockDcosClient = Mock(DCOS)
  DeployDcosServerGroupDescriptionToAppMapper mockDcosDescriptionToAppMapper = Mock(DeployDcosServerGroupDescriptionToAppMapper)

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', 'user', 'pw'
  )

  DcosClientProvider mockDcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> mockDcosClient
  }

  DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
    application: APPLICATION_NAME.service.app, region: APPLICATION_NAME.region, credentials: testCredentials, stack: APPLICATION_NAME.service.stack,
    freeFormDetails: APPLICATION_NAME.service.detail, desiredCapacity: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
          docker: new DeployDcosServerGroupDescription.Docker(forcePullImage: false, privileged: false,
                  network: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "Bridge"),
                  image: new DeployDcosServerGroupDescription.Image(imageId: "test")))

  App application = new App(id: APPLICATION_NAME.toString(), instances: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
    container: new Container(docker: new Docker(image: "test", forcePullImage: false, privileged: false, portMappings: [], network: "BRIDGE")),
    versionInfo: new VersionInfo(lastConfigChangeAt: null)
  )

  @Subject
  AtomicOperation atomicOperation = new DeployDcosServerGroupAtomicOperation(mockDcosClientProvider, mockDcosDescriptionToAppMapper, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {
    given:
    mockDcosClient.getApps(_) >> new GetAppNamespaceResponse(apps: [])
    mockDcosDescriptionToAppMapper.map(_, _) >> application

    when:
    DeploymentResult deploymentResult = atomicOperation.operate([])

    then:
    noExceptionThrown()
    deploymentResult != null
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains(String.format("%s:%s", "${APPLICATION_NAME.account}_${APPLICATION_NAME.region}", APPLICATION_NAME.service.group))
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion.get("${APPLICATION_NAME.account}_${APPLICATION_NAME.region}".toString()) == APPLICATION_NAME.service.group
  }
}
