package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class UpsertTencentScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY"

  UpsertTencentScalingPolicyDescription description

  @Autowired
  TencentClusterProvider tencentClusterProvider

  UpsertTencentScalingPolicyAtomicOperation(UpsertTencentScalingPolicyDescription description) {
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

    task.updateStatus BASE_PHASE, "Initializing upsert scaling policy $serverGroupName in $region..."

    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

    if(description.operationType == UpsertTencentScalingPolicyDescription.OperationType.CREATE) {
      task.updateStatus BASE_PHASE, "create scaling policy in $serverGroupName..."
      def scalingPolicyId = client.createScalingPolicy asgId, description
      task.updateStatus BASE_PHASE, "new scaling policy $scalingPolicyId is created."
    } else if (description.operationType == UpsertTencentScalingPolicyDescription.OperationType.MODIFY) {
      def scalingPolicyId = description.scalingPolicyId
      task.updateStatus BASE_PHASE, "update scaling policy $scalingPolicyId in $serverGroupName..."
      client.modifyScalingPolicy(scalingPolicyId, description)
    } else {
      throw new TencentOperationException("unknown operation type, operation quit.")
    }

    task.updateStatus BASE_PHASE, "Complete upsert scaling policy."
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
