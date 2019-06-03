package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription

class DeleteTencentScheduledActionAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SCHEDULED_ACTION"

  DeleteTencentScheduledActionDescription description

  DeleteTencentScheduledActionAtomicOperation(DeleteTencentScheduledActionDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def region = description.region
    def scheduledActionId = description.scheduledActionId
    def serverGroupName = description.serverGroupName
    task.updateStatus BASE_PHASE, "Initializing delete scheduled action $scheduledActionId in $serverGroupName..."

    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )
    client.deleteScheduledAction(scheduledActionId)
    task.updateStatus(BASE_PHASE, "Complete delete scheduled action. ")
    return null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
