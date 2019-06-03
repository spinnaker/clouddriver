package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class DestroyTencentServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  DestroyTencentServerGroupDescription description

  @Autowired
  TencentClusterProvider tencentClusterProvider

  DestroyTencentServerGroupAtomicOperation(DestroyTencentServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destroy server group $description.serverGroupName in " +
      "$description.region..."
    def region = description.region
    def accountName = description.accountName
    def serverGroupName = description.serverGroupName

    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )
    task.updateStatus(BASE_PHASE, "Start destroy server group $serverGroupName")
    def serverGroup = tencentClusterProvider.getServerGroup(accountName, region, serverGroupName, false)

    if (serverGroup) {
      String asgId = serverGroup.asg.autoScalingGroupId
      String ascId = serverGroup.asg.launchConfigurationId

      task.updateStatus(BASE_PHASE, "Server group $serverGroupName is related to " +
        "auto scaling group $asgId and launch configuration $ascId.")

      task.updateStatus(BASE_PHASE, "Deleting auto scaling group $asgId...")
      client.deleteAutoScalingGroup(asgId)
      task.updateStatus(BASE_PHASE, "Auto scaling group $asgId is deleted.")

      task.updateStatus(BASE_PHASE, "Deleting launch configuration $ascId...")
      client.deleteLaunchConfiguration(ascId)
      task.updateStatus(BASE_PHASE, "Launch configuration $ascId is deleted.")

      task.updateStatus(BASE_PHASE, "Complete destroy server group $serverGroupName.")
    } else {
      task.updateStatus(BASE_PHASE, "Server group $serverGroupName is not found.")
    }

    task.updateStatus(BASE_PHASE, "Complete destroy server group. ")
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
