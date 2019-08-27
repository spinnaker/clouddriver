package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.handlers.TencentDeployHandler
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class CloneTencentServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "CLONE_SERVER_GROUP"

  TencentDeployDescription description

  @Autowired
  TencentClusterProvider tencentClusterProvider

  @Autowired
  TencentDeployHandler tencentDeployHandler

  CloneTencentServerGroupAtomicOperation(TencentDeployDescription description) {
    this.description = description
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    def newDescription = cloneAndOverrideDescription()
    def result = tencentDeployHandler.handle(newDescription, priorOutputs)
    copyScalingPolicyAndScheduledAction(result)
    result
  }

  private def copyScalingPolicyAndScheduledAction(DeploymentResult deployResult) {
    String sourceServerGroupName = description.source.serverGroupName
    String sourceRegion = description.source.region
    String accountName = description.accountName
    def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)

    if (!sourceServerGroup) {
      return
    }

    String sourceAsgId = sourceServerGroup.asg.autoScalingGroupId

    task.updateStatus BASE_PHASE, "Initializing copy scaling policy and scheduled action from $sourceAsgId."

    AutoScalingClient autoScalingClient = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      sourceRegion
    )

    String newServerGroupName = deployResult.serverGroupNameByRegion[sourceRegion]
    def newAsg = autoScalingClient.getAutoScalingGroupsByName(newServerGroupName)[0]
    String newAsgId = newAsg.autoScalingGroupId

    // copy all scaling policies
    def scalingPolicies = autoScalingClient.getScalingPolicies(sourceAsgId)
    for (scalingPolicy in scalingPolicies) {
      try {
        def scalingPolicyDescription = new UpsertTencentScalingPolicyDescription().with {
          it.serverGroupName = newServerGroupName
          it.region = sourceRegion
          it.accountName = accountName
          it.operationType = UpsertTencentScalingPolicyDescription.OperationType.CREATE
          it.adjustmentType = scalingPolicy.adjustmentType
          it.adjustmentValue = scalingPolicy.adjustmentValue
          it.metricAlarm = scalingPolicy.metricAlarm
          // it.notificationUserGroupIds = scalingPolicy.notificationUserGroupIds
          it.cooldown = scalingPolicy.cooldown
          it
        }
        autoScalingClient.createScalingPolicy(newAsgId, scalingPolicyDescription)
      } catch (TencentCloudSDKException sdk_e) {
        // something bad happened during creation, log the error and continue
        log.warn "create scaling policy error $sdk_e"
      }
    }

    // copy all scheduled actions
    def scheduledActions = autoScalingClient.getScheduledAction(sourceAsgId)
    for (scheduledAction in scheduledActions) {
      try {
        def original_start_time = Date.parse("yyyy-MM-dd'T'HH:mm:ss'+08:00'", scheduledAction.startTime)
        def current_time = new Date()
        def new_start_time

        if (original_start_time < current_time) {
          log.info('original start time is before current time')
          if (scheduledAction.endTime == "0000-00-00T00:00:00+08:00") {
            // schedule action just run for once, and had finished
            continue
          } else {
            log.info('scheduled action is for once, set new start time to current time')
            new_start_time = current_time
          }
        } else {
          log.info('scheduled action is not trigger, use original start time')
          new_start_time = original_start_time
        }

        def scheduledActionDescription = new UpsertTencentScheduledActionDescription().with {
          it.serverGroupName = newServerGroupName
          it.region = sourceRegion
          it.accountName = accountName
          it.operationType = UpsertTencentScheduledActionDescription.OperationType.CREATE
          it.maxSize = scheduledAction.maxSize
          it.minSize = scheduledAction.minSize
          it.desiredCapacity = scheduledAction.desiredCapacity
          it.startTime = new_start_time.format("yyyy-MM-dd'T'HH:mm:ss'+08:00'")
          it.endTime = scheduledAction.endTime
          it.recurrence = scheduledAction.recurrence
          it
        }
        autoScalingClient.createScheduledAction(newAsgId as String, scheduledActionDescription)
      } catch (TencentCloudSDKException sdk_e) {
        // something bad happened during creation, log the error and continue
        log.warn "create scheduled action error $sdk_e"
      }
    }
  }

  private TencentDeployDescription cloneAndOverrideDescription() {
    def newDescription = description.clone()

    if (!description?.source?.region || !description?.source?.serverGroupName) {
      return newDescription
    }

    String sourceServerGroupName = description.source.serverGroupName
    String sourceRegion = description.source.region
    String accountName = description.accountName
    task.updateStatus BASE_PHASE, "Initializing copy of server group $sourceServerGroupName..."

    // look up source server group
    def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)

    if (!sourceServerGroup) {
      return newDescription
    }

    // start override source description
    newDescription.region = description.region ?: sourceRegion
    newDescription.application = description.application ?: sourceServerGroup.moniker.app
    newDescription.stack = description.stack ?: sourceServerGroup.moniker.stack
    newDescription.detail = description.detail ?: sourceServerGroup.moniker.detail

    def sourceLaunchConfig = sourceServerGroup.launchConfig
    if (sourceLaunchConfig) {
      newDescription.instanceType = description.instanceType ?: sourceLaunchConfig.instanceType
      newDescription.imageId = description.imageId ?: sourceLaunchConfig.imageId
      newDescription.projectId = description.projectId ?: sourceLaunchConfig.projectId as Integer
      newDescription.systemDisk = description.systemDisk ?: sourceLaunchConfig.systemDisk as Map
      newDescription.dataDisks = description.dataDisks ?: sourceLaunchConfig.dataDisks as List
      newDescription.internetAccessible = description.internetAccessible ?: sourceLaunchConfig.internetAccessible as Map
      newDescription.loginSettings = description.loginSettings ?: sourceLaunchConfig.loginSettings as Map
      newDescription.securityGroupIds = description.securityGroupIds ?: sourceLaunchConfig.securityGroupIds as List
      newDescription.enhancedService = description.enhancedService ?: sourceLaunchConfig.enhancedService as Map
      newDescription.userData = description.userData ?: sourceLaunchConfig.userData
      newDescription.instanceChargeType = description.instanceChargeType ?: sourceLaunchConfig.instanceChargeType
      // newDescription.instanceTypes = description.instanceTypes ?: sourceLaunchConfig.instanceTypes as List
      newDescription.instanceMarketOptionsRequest = description.instanceMarketOptionsRequest ?: sourceLaunchConfig.instanceMarketOptionsRequest as Map
      newDescription.instanceTypesCheckPolicy = description.instanceTypesCheckPolicy ?: sourceLaunchConfig.instanceTypesCheckPolicy



      if (description.instanceTags) {
        newDescription.instanceTags = description.instanceTags
      } else if (sourceLaunchConfig.instanceTags) {
        def cloneInstanceTags = []
        for (tag in sourceLaunchConfig.instanceTags) {
          if (tag.key != AutoScalingClient.defaultServerGroupTagKey) {
            cloneInstanceTags.add(tag)
          }
        }
        newDescription.instanceTags = cloneInstanceTags
      }
    }

    def sourceAutoScalingGroup = sourceServerGroup.asg
    if (sourceAutoScalingGroup) {
      newDescription.maxSize = description.maxSize ?: sourceAutoScalingGroup.maxSize as Integer
      newDescription.minSize = description.minSize ?: sourceAutoScalingGroup.minSize as Integer
      newDescription.desiredCapacity = description.desiredCapacity ?: sourceAutoScalingGroup.desiredCapacity as Integer
      newDescription.vpcId = description.vpcId ?: sourceAutoScalingGroup.vpcId

      if (newDescription.vpcId) {
        newDescription.subnetIds = description.subnetIds ?: sourceAutoScalingGroup.subnetIdSet as List
      } else {
        newDescription.zones = description.zones ?: sourceAutoScalingGroup.zoneSet as List
      }

      newDescription.defaultCooldown = description.defaultCooldown ?: sourceAutoScalingGroup.defaultCooldown as Integer
      newDescription.terminationPolicies = description.terminationPolicies ?: sourceAutoScalingGroup.terminationPolicies as List
      newDescription.loadBalancerIds = description.loadBalancerIds ?: sourceAutoScalingGroup.loadBalancerIds as List
      newDescription.forwardLoadBalancers = description.forwardLoadBalancers ?: sourceAutoScalingGroup.forwardLoadBalancerSet as List
      newDescription.retryPolicy = description.retryPolicy ?: sourceAutoScalingGroup.retryPolicy
      newDescription.zonesCheckPolicy = description.zonesCheckPolicy ?: sourceAutoScalingGroup.zoneCheckPolicy
    }
    newDescription
  }

    private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
