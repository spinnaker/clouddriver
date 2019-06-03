package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule
import groovy.util.logging.Slf4j

@Slf4j
class UpsertTencentSecurityGroupAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP"
  UpsertTencentSecurityGroupDescription description

  UpsertTencentSecurityGroupAtomicOperation(UpsertTencentSecurityGroupDescription description) {
    this.description = description
  }


  @Override
  Map operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing upsert of Tencent securityGroup ${description.securityGroupName} " +
      "in ${description.region}...")
    log.info("params = ${description}")

    def securityGroupId = description.securityGroupId
    if (securityGroupId?.length() > 0) {
      updateSecurityGroup(description)
    }else {
      insertSecurityGroup(description)
    }

    log.info("upsert securityGroup name:${description.securityGroupName}, id:${description.securityGroupId}")
    return [securityGroups: [(description.region): [name: description.securityGroupName, id: description.securityGroupId]]]
  }


  private String updateSecurityGroup(UpsertTencentSecurityGroupDescription description) {
    task.updateStatus(BASE_PHASE, "Start update securityGroup ${description.securityGroupName} ${description.securityGroupId} ...")
    def securityGroupId = description.securityGroupId
    def vpcClient = new VirtualPrivateCloudClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def oldGroupRules = vpcClient.getSecurityGroupPolicies(securityGroupId)
    def newGroupInRules = description.inRules

    //del in rules
    def delGroupInRules = [] as List<TencentSecurityGroupRule>
    oldGroupRules?.Ingress?.each { ingress ->
      def keepRule = newGroupInRules.find {
        it.index.equals(ingress.PolicyIndex)
      }
      if (keepRule == null) {
        def delInRule = new TencentSecurityGroupRule(index: ingress.policyIndex)
        delGroupInRules.add(delInRule)
      }
    }
    if (!delGroupInRules.isEmpty()) {
      task.updateStatus(BASE_PHASE, "Start delete securityGroup ${securityGroupId} rules ...")
      vpcClient.deleteSecurityGroupInRules(securityGroupId, delGroupInRules)
      task.updateStatus(BASE_PHASE, "delete securityGroup ${securityGroupId} rules end")
    }
    //add in rules
    def addGroupInRules = [] as List<TencentSecurityGroupRule>
    newGroupInRules?.each {
      if (it.index == null) {
        addGroupInRules.add(it)
      }
    }
    if (!addGroupInRules.isEmpty()) {
      task.updateStatus(BASE_PHASE, "Start add securityGroup ${securityGroupId} rules ...")
      vpcClient.createSecurityGroupRules(securityGroupId, addGroupInRules, null)
      task.updateStatus(BASE_PHASE, "add securityGroup ${securityGroupId} rules end")
    }

    task.updateStatus(BASE_PHASE, "Update securityGroup ${description.securityGroupName} ${description.securityGroupId} end")
    return ""
  }


  private String insertSecurityGroup(UpsertTencentSecurityGroupDescription description) {
    task.updateStatus(BASE_PHASE, "Start create new securityGroup ${description.securityGroupName} ...")

    def vpcClient = new VirtualPrivateCloudClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def securityGroupId = vpcClient.createSecurityGroup(description.securityGroupName, description.securityGroupDesc)
    description.securityGroupId = securityGroupId
    task.updateStatus(BASE_PHASE, "Create new securityGroup ${description.securityGroupName} success, id is ${securityGroupId}.")

    if (description.inRules?.size() > 0) {
      task.updateStatus(BASE_PHASE, "Start create new securityGroup rules in ${securityGroupId} ...")
      vpcClient.createSecurityGroupRules(securityGroupId, description.inRules, description.outRules)
      task.updateStatus(BASE_PHASE, "Create new securityGroup rules in ${securityGroupId} end")
    }
    return ""
  }


  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

}
