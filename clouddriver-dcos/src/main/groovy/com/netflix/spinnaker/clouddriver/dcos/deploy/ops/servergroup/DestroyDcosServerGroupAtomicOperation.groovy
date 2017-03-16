package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.Result

class DestroyDcosServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY"

  final DcosClientProvider dcosClientProvider
  final DestroyDcosServerGroupDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DestroyDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DestroyDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "serverGroupName": "echoserver2", "credentials": "my-dcos-account" } ]' localhost:7002/dcos/ops/destroyServerGroup
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Destroying marathon application: ${description.serverGroupName}..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials)
    def appId = DcosSpinnakerAppId.from(description.credentials.name, description.region, description.serverGroupName).get()

    Result deleteResult = dcosClient.deleteApp(appId.toString())

    task.updateStatus BASE_PHASE, "Successfully issued delete request for application id '$appId'. " +
            "Corresponding marathon deployment id=$deleteResult.deploymentId"

    task.updateStatus BASE_PHASE, "Completed destroy server group operation for $appId"
  }
}
