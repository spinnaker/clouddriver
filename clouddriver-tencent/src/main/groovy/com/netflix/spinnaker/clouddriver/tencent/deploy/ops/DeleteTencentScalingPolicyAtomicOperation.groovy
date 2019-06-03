package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScalingPolicyDescription

class DeleteTencentScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY"

  DeleteTencentScalingPolicyDescription description

  DeleteTencentScalingPolicyAtomicOperation(DeleteTencentScalingPolicyDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def region = description.region
    def scalingPolicyId = description.scalingPolicyId
    def serverGroupName = description.serverGroupName

    task.updateStatus BASE_PHASE, "Initializing delete scaling policy $scalingPolicyId in $serverGroupName..."
    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )
    client.deleteScalingPolicy(scalingPolicyId)
    task.updateStatus(BASE_PHASE, "Complete delete scaling policy. ")
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
