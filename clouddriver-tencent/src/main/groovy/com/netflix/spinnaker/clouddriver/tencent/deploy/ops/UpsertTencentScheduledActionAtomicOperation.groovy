package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class UpsertTencentScheduledActionAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_SCHEDULED_ACTIONS"

  UpsertTencentScheduledActionDescription description

  @Autowired
  TencentClusterProvider tencentClusterProvider

  UpsertTencentScheduledActionAtomicOperation(UpsertTencentScheduledActionDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def region = description.region
    def serverGroupName = description.serverGroupName
    def accountName = description.accountName
    def asgId = tencentClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region)

    if (!asgId) {
      throw new TencentOperationException("ASG of $serverGroupName is not found.")
    }

    task.updateStatus BASE_PHASE, "Initializing upsert scheduled action $serverGroupName in $region..."

    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

    if(description.operationType == UpsertTencentScheduledActionDescription.OperationType.CREATE) {
      task.updateStatus BASE_PHASE, "create scheduled action in $serverGroupName..."
      def scalingPolicyId = client.createScheduledAction asgId, description
      task.updateStatus BASE_PHASE, "new scheduled action $scalingPolicyId is created."
    } else if (description.operationType == UpsertTencentScheduledActionDescription.OperationType.MODIFY) {
      def scheduledActionId = description.scheduledActionId
      task.updateStatus BASE_PHASE, "update scheduled action $scheduledActionId in $serverGroupName..."
      client.modifyScheduledAction scheduledActionId, description
    } else {
      throw new TencentOperationException("unknown operation type, operation quit.")
    }

    task.updateStatus BASE_PHASE, "Complete upsert scheduled action."
    null
    return null
  }


  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
