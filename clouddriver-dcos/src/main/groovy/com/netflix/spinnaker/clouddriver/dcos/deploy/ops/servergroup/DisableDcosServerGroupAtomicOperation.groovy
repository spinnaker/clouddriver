package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DisableDcosServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DISABLE"

  final DcosClientProvider dcosClientProvider
  final DisableDcosServerGroupDescription description

  DisableDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DisableDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing disable of server group $description.serverGroupName..."

    task.updateStatus BASE_PHASE, "Setting number of instances to 0 for $description.serverGroupName..."

    // TODO Most providers just take the instances out of load. Probably what we'd rather do.
    // TODO pull this out into a common place instead and reuse it both places? pass in BASE_PHASE
    def resizeDesc = new ResizeDcosServerGroupDescription().with {
      region = description.region
      serverGroupName = description.serverGroupName
      targetSize = 0
      account = description.account
      credentials = description.credentials
      it
    }

    def resizeOp = new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, resizeDesc)
    resizeOp.operate([])

    return null
  }
}