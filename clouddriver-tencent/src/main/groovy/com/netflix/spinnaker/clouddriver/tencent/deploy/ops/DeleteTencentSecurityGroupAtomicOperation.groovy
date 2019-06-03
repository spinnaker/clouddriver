package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription
import groovy.util.logging.Slf4j

@Slf4j
class DeleteTencentSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SECURITY_GROUP"
  DeleteTencentSecurityGroupDescription description

  DeleteTencentSecurityGroupAtomicOperation(DeleteTencentSecurityGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing delete of Tencent securityGroup ${description.securityGroupId} " +
      "in ${description.region}...")

    def vpcClient = new VirtualPrivateCloudClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def securityGroupId = description.securityGroupId
    task.updateStatus(BASE_PHASE, "Start delete securityGroup ${securityGroupId} ...")
    vpcClient.deleteSecurityGroup(securityGroupId)
    task.updateStatus(BASE_PHASE, "Delete securityGroup ${securityGroupId} end")

    return null
  }


  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
