package com.netflix.spinnaker.clouddriver.tencent.controllers

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/*
curl -X GET \
  'http://localhost:7002/applications/myapp/clusters/test/myapp-dev/tencent/serverGroups/myapp-dev-v007/scalingActivities?region=ap-guangzhou' \
  -H 'cache-control: no-cache'
*/

@RestController
@RequestMapping("/applications/{application}/clusters/{account}/{clusterName}/tencent/serverGroups/{serverGroupName}")
class TencentServerGroupController {
  final static int MAX_SCALING_ACTIVITIES = 500

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  TencentClusterProvider tencentClusterProvider

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  ResponseEntity getScalingActivities(
    @PathVariable String account,
    @PathVariable String serverGroupName,
    @RequestParam(value = "region", required = true) String region) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!(credentials instanceof TencentNamedAccountCredentials)) {
      return new ResponseEntity(
        [message: "${account} is not tencent credential type"],
        HttpStatus.BAD_REQUEST
      )
    }
    def serverGroup = tencentClusterProvider.getServerGroup(account, region, serverGroupName, false)
    String autoScalingGroupId = serverGroup.asg.autoScalingGroupId
    def client = new AutoScalingClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )
    def scalingActivities = client.getAutoScalingActivitiesByAsgId(autoScalingGroupId, MAX_SCALING_ACTIVITIES)
    return new ResponseEntity(scalingActivities, HttpStatus.OK)
  }
}
