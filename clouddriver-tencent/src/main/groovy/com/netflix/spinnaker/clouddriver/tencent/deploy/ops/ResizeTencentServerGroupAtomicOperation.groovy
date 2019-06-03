package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class ResizeTencentServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  @Autowired
  TencentClusterProvider tencentClusterProvider

  private final ResizeTencentServerGroupDescription description

  ResizeTencentServerGroupAtomicOperation(ResizeTencentServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName in " +
      "$description.region..."
    def accountName = description.accountName
    //def credentials = description.credentials
    def region = description.region
    def serverGroupName = description.serverGroupName
    def asgId = tencentClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region)

    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )
    client.resizeAutoScalingGroup(asgId, description.capacity)
    task.updateStatus BASE_PHASE, "Complete resize of server group $description.serverGroupName in " +
      "$description.region."
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
