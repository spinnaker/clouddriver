package com.netflix.spinnaker.clouddriver.tencent.deploy.handlers

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.tencent.deploy.TencentServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
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

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    description.serverGroupName = serverGroupName

    AutoScalingClient autoScalingClient = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    autoScalingClient.deploy(description)

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName in $region."

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    deploymentResult
    return deploymentResult
  }
}
