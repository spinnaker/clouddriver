package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper.DeployDcosServerGroupDescriptionToAppMapper
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.model.DCOSAuthCredentials
import mesosphere.marathon.client.model.v2.*
import spock.lang.Specification
import spock.lang.Subject

class DeployDcosServerGroupAtomicOperationSpec extends Specification {
  private static final APPLICATION_NAME = DcosSpinnakerAppId.parse('/test/region/api-test-detail-v000', true).get()

  DCOS mockDcosClient = Mock(DCOS)
  DeployDcosServerGroupDescriptionToAppMapper mockDcosDescriptionToAppMapper = Mock(DeployDcosServerGroupDescriptionToAppMapper)

  DcosCredentials testCredentials = new DcosCredentials(
    'test', 'test', 'test', 'https://test.url.com', DCOSAuthCredentials.forUserAccount('user', 'pw')
  )

  DcosClientProvider mockDcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials) >> mockDcosClient
  }

  DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
    application: APPLICATION_NAME.serverGroupName.app, region: APPLICATION_NAME.unsafeRegion, credentials: testCredentials, stack: APPLICATION_NAME.serverGroupName.stack,
    freeFormDetails: APPLICATION_NAME.serverGroupName.detail, desiredCapacity: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
          docker: new DeployDcosServerGroupDescription.Docker(forcePullImage: false, privileged: false,
                  network: "BRIDGE",
                  image: new DeployDcosServerGroupDescription.Image(imageId: "test")))

  App application = new App(id: APPLICATION_NAME.toString(), instances: 1, cpus: 0.25, mem: 128, disk: 0, gpus: 0,
    container: new Container(docker: new Docker(image: "test", forcePullImage: false, privileged: false, portMappings: [], network: "BRIDGE")),
    versionInfo: new AppVersionInfo(lastConfigChangeAt: null)
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
    deploymentResult.serverGroupNames && deploymentResult.serverGroupNames.contains(String.format("%s:%s", "${APPLICATION_NAME.unsafeRegion}", APPLICATION_NAME.serverGroupName.group))
    deploymentResult.serverGroupNameByRegion && deploymentResult.serverGroupNameByRegion.get("${APPLICATION_NAME.unsafeRegion}".toString()) == APPLICATION_NAME.serverGroupName.group
  }
}
