package com.netflix.spinnaker.clouddriver.tencent.deploy.handlers

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.tencent.deploy.TencentServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/*
curl -X POST \
  http://localhost:7002/tencent/ops \
  -H 'Content-Type: application/json' \
  -H 'Postman-Token: 16583564-d31a-442f-bb17-0a308ee2c529' \
  -H 'cache-control: no-cache' \
  -d '[{"createServerGroup":{"application":"myapp","stack":"dev","accountName":"test","imageId":"img-oikl1tzv","instanceType":"S2.SMALL2","zones":["ap-guangzhou-2"],"credentials":"my-account-name","maxSize":0,"minSize":0,"desiredCapacity":0,"vpcId":"","region":"ap-guangzhou","dataDisks":[{"diskType":"CLOUD_PREMIUM","diskSize":50}],"systemDisk":{"diskType":"CLOUD_PREMIUM","diskSize":50}}}]'
*/


@Component
@Slf4j
class TencentDeployHandler implements DeployHandler<TencentDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  private TencentClusterProvider tencentClusterProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof TencentDeployDescription
  }

  @Override
  DeploymentResult handle(TencentDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deployment to ${description.zones}"

    def accountName = description.accountName
    def region = description.region
    def serverGroupNameResolver = new TencentServerGroupNameResolver(
      accountName, region, tencentClusterProvider, description.credentials)

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.detail, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    description.serverGroupName = serverGroupName

    AutoScalingClient autoScalingClient = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

    if (description?.source?.useSourceCapacity) {
      log.info('copy source server group capacity')
      String sourceServerGroupName = description?.source?.serverGroupName
      String sourceRegion = description?.source?.region
      def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)
      if (!sourceServerGroup) {
        log.warn("source server group $sourceServerGroupName is not found")
      } else {
        description.desiredCapacity = sourceServerGroup.asg.desiredCapacity as Integer
        description.maxSize = sourceServerGroup.asg.maxSize as Integer
        description.minSize = sourceServerGroup.asg.minSize as Integer
      }
    }

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    autoScalingClient.deploy(description)

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName in $region."

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName

    if (description.copySourceScalingPoliciesAndActions) {
      copyScalingPolicyAndScheduledAction(description, deploymentResult)
      copyNotification(description, deploymentResult)  // copy notification by the way
    }

    return deploymentResult
  }

  private def copyNotification(TencentDeployDescription description, DeploymentResult deployResult) {
    task.updateStatus BASE_PHASE, "Enter copyNotification."
    String sourceServerGroupName = description?.source?.serverGroupName
    String sourceRegion = description?.source?.region
    String accountName = description?.accountName
    def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)

    if (!sourceServerGroup) {
      log.warn("source server group not found, account $accountName, region $sourceRegion, source sg name $sourceServerGroupName")
      return
    }

    String sourceAsgId = sourceServerGroup.asg.autoScalingGroupId

    task.updateStatus BASE_PHASE, "Initializing copy notification from $sourceAsgId."

    AutoScalingClient autoScalingClient = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      sourceRegion
    )

    String newServerGroupName = deployResult.serverGroupNameByRegion[sourceRegion]
    def newAsg = autoScalingClient.getAutoScalingGroupsByName(newServerGroupName)[0]
    String newAsgId = newAsg.autoScalingGroupId

    def notifications = autoScalingClient.getNotification(sourceAsgId)
    for (notification in notifications) {
      try {
        autoScalingClient.createNotification(newAsgId, notification)
      } catch (TencentOperationException toe) {
        // something bad happened during creation, log the error and continue
        log.warn "create notification error $toe"
      }
    }
  }

  private def copyScalingPolicyAndScheduledAction(TencentDeployDescription description, DeploymentResult deployResult) {
    task.updateStatus BASE_PHASE, "Enter copyScalingPolicyAndScheduledAction."

    String sourceServerGroupName = description?.source?.serverGroupName
    String sourceRegion = description?.source?.region
    String accountName = description?.accountName
    def sourceServerGroup = tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName)

    if (!sourceServerGroup) {
      log.warn("description is $description")
      log.warn("source server group not found, account $accountName, region $sourceRegion, source sg name $sourceServerGroupName")
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
}
