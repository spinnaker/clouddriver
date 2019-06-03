package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class TerminateTencentInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  @Autowired
  TencentClusterProvider tencentClusterProvider

  TerminateTencentInstancesDescription description

  TerminateTencentInstancesAtomicOperation(TerminateTencentInstancesDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def region = description.region
    def serverGroupName = description.serverGroupName
    def instanceIds = description.instanceIds
    def accountName = description.accountName

    task.updateStatus BASE_PHASE, "Initializing termination of instances (${description.instanceIds.join(", ")}) in " +
      "$description.region:$serverGroupName..."

    def client = new CloudVirtualMachineClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )
    client.terminateInstances(instanceIds)

    task.updateStatus BASE_PHASE, "Complete termination of instance."
    return null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
