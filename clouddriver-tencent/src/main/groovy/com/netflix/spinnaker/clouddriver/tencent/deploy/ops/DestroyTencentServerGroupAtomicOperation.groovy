package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DestroyTencentServerGroupDescription
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
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
    // 1. detach all instances from asg
    // 2. terminate detached instances
    // 3. delete asg
    // 4. delete asc
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

    def cvmClient = new CloudVirtualMachineClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

      task.updateStatus(BASE_PHASE, "Start destroy server group $serverGroupName")
    def serverGroup = tencentClusterProvider.getServerGroup(accountName, region, serverGroupName, true)

    if (serverGroup) {
      String asgId = serverGroup.asg.autoScalingGroupId
      String ascId = serverGroup.asg.launchConfigurationId
      Set<TencentInstance> instances = serverGroup.instances
      def instanceIds = instances.collect {
        it.name
      }

      task.updateStatus(BASE_PHASE, "Server group $serverGroupName is related to " +
        "auto scaling group $asgId and launch configuration $ascId.")

      if (instanceIds) {
        Integer maxQueryTime = 10000
        task.updateStatus(BASE_PHASE, "Will detach $instanceIds from $asgId")
        def activityId = client.detachInstances(asgId, instanceIds).activityId

        for (def i=0; i<maxQueryTime;i++) {
          def response = client.describeAutoScalingActivities(activityId)
          if (response.activitySet) {
            def activity = response.activitySet[0]
            def activity_status = activity.statusCode

            if (activity_status == 'SUCCESSFUL') {
              log.info('detach activity is done')
              break
            } else if (activity_status == 'RUNNING' || activity_status == 'INIT') {
              log.info('detach activity is running')
            } else {
              log.error('detach activity is cancelled or failed')
              throw new TencentOperationException('detach activity is cancelled or failed')
            }
            sleep(1000)
          } else {
            log.warn('found no activity')
          }
        }

        task.updateStatus(BASE_PHASE, "Detach activity has finished, will start terminate soon.")
        cvmClient.terminateInstances(instanceIds)
        task.updateStatus(BASE_PHASE, "$instanceIds are terminaing.")
      }

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
