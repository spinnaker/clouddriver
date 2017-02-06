package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.App

class ResizeDcosServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE"

  final DcosClientProvider dcosClientProvider
  final ResizeDcosServerGroupDescription description

  ResizeDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, ResizeDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "kub-test-v000", "capacity": { "desired": 7 }, "account": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials)
    def appId = DcosSpinnakerId.from(description.credentials.name, description.region, description.serverGroupName)
    def size = description.instances

    task.updateStatus BASE_PHASE, "Checking to see if $appId already exists..."

    def maybeApp = dcosClient.maybeApp(appId.toString())

    if (!maybeApp.present) {
      throw new RuntimeException("$appId does not exist in DCOS definitions.")
    }

    task.updateStatus BASE_PHASE, "Setting size to $size..."

    def app = new App()

    app.with {
      id = appId.toString()
      instances = size
    }

    dcosClient.modifyApp(appId.toString(), app)

    task.updateStatus BASE_PHASE, "Completed resize operation."
  }
}

