package com.netflix.spinnaker.clouddriver.tencent.client

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.tencentcloudapi.as.v20180419.AsClient
import com.tencentcloudapi.as.v20180419.models.*
import com.tencentcloudapi.clb.v20180317.ClbClient
import com.tencentcloudapi.clb.v20180317.models.*
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import com.tencentcloudapi.common.profile.ClientProfile
import com.tencentcloudapi.common.profile.HttpProfile
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class AutoScalingClient extends AbstractTencentServiceClient {
  final String endPoint = "as.tencentcloudapi.com"
  static String defaultServerGroupTagKey = "spinnaker:server-group-name"
  final Integer DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME = 1000

  private AsClient client
  private ClbClient clbClient // todo move to load balancer client ?

  AutoScalingClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey)

    client = new AsClient(cred, region, clientProfile)

    String clbEndPoint = "clb.tencentcloudapi.com"
    def clbHttpProfile = new HttpProfile()
    clbHttpProfile.setEndpoint(clbEndPoint)

    def clbClientProfile = new ClientProfile()
    clbClientProfile.setHttpProfile(clbHttpProfile)

    clbClient = new ClbClient(cred, region, clbClientProfile)
  }

  String deploy(TencentDeployDescription description) {
    try {
      // 1. create launch configuration
      CreateLaunchConfigurationRequest createLaunchConfigurationRequest = buildLaunchConfigurationRequest(description)
      CreateLaunchConfigurationResponse createLaunchConfigurationResponse = client.CreateLaunchConfiguration(createLaunchConfigurationRequest)
      String launchConfigurationId = createLaunchConfigurationResponse.launchConfigurationId

      try {
        // 2. create auto scaling group
        CreateAutoScalingGroupRequest createAutoScalingGroupRequest = buildAutoScalingGroupRequest(description, launchConfigurationId)
        CreateAutoScalingGroupResponse createAutoScalingGroupResponse = client.CreateAutoScalingGroup(createAutoScalingGroupRequest)
        createAutoScalingGroupResponse.autoScalingGroupId
      } catch (TencentCloudSDKException e) {
        // if create auto scaling group failed, delete launch configuration.
        sleep(5000)  // wait for a while before delete launch configuration
        log.error(e.toString())
        DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest()
        request.launchConfigurationId = launchConfigurationId
        client.DeleteLaunchConfiguration(request)
        throw e
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  private static def buildLaunchConfigurationRequest(TencentDeployDescription description) {
    CreateLaunchConfigurationRequest createLaunchConfigurationRequest = new CreateLaunchConfigurationRequest()

    def launchConfigurationName = description.serverGroupName
    createLaunchConfigurationRequest.launchConfigurationName = launchConfigurationName
    createLaunchConfigurationRequest.imageId = description.imageId

    if (description.projectId) {
      createLaunchConfigurationRequest.projectId = description.projectId
    }

    if (description.instanceType) {
      createLaunchConfigurationRequest.instanceType = description.instanceType
    }

    if (description.systemDisk) {
      SystemDisk systemDisk = new SystemDisk()
      systemDisk.diskSize = description.systemDisk.diskSize as Integer
      systemDisk.diskType = description.systemDisk.diskType
      createLaunchConfigurationRequest.systemDisk = systemDisk
    }

    if (description.dataDisks) {
      createLaunchConfigurationRequest.dataDisks = description.dataDisks.collect {
        def dataDisk = new DataDisk()
        dataDisk.diskType = it.diskType
        dataDisk.diskSize = it.diskSize as Integer
        dataDisk.snapshotId = it.snapShotId
        dataDisk
      }
    }

    if (description.internetAccessible) {
      InternetAccessible internetAccessible = new InternetAccessible()
      internetAccessible.internetChargeType = description.internetAccessible.internetChargeType
      internetAccessible.internetMaxBandwidthOut = description.internetAccessible.internetMaxBandwidthOut as Integer
      internetAccessible.publicIpAssigned = description.internetAccessible.publicIpAssigned
      createLaunchConfigurationRequest.internetAccessible = internetAccessible
    }

    if (description.loginSettings) {
      LoginSettings loginSettings = new LoginSettings()
      loginSettings.keepImageLogin = description.loginSettings.keepImageLogin
      loginSettings.keyIds = description.loginSettings.keyIds
      loginSettings.password = description.loginSettings.password
      createLaunchConfigurationRequest.loginSettings = loginSettings
    }

    if (description.securityGroupIds) {
      createLaunchConfigurationRequest.securityGroupIds = description.securityGroupIds
    }

    if (description.enhancedService) {
      EnhancedService enhancedService = new EnhancedService()
      enhancedService.monitorService = new RunMonitorServiceEnabled()
      enhancedService.monitorService.enabled = description.enhancedService.monitorService.enabled

      enhancedService.securityService = new RunSecurityServiceEnabled()
      enhancedService.securityService.enabled = description.enhancedService.securityService.enabled

      createLaunchConfigurationRequest.enhancedService = enhancedService
    }

    if (description.userData) {
      createLaunchConfigurationRequest.userData = description.userData
    }

    if (description.instanceChargeType) {
      createLaunchConfigurationRequest.instanceChargeType = description.instanceChargeType
    }

    if (description.instanceMarketOptionsRequest) {
      InstanceMarketOptionsRequest instanceMarketOptionsRequest = new InstanceMarketOptionsRequest()
      instanceMarketOptionsRequest.marketType = description.instanceMarketOptionsRequest.marketType

      SpotMarketOptions spotOptions = new SpotMarketOptions()
      spotOptions.maxPrice = description.instanceMarketOptionsRequest.spotMarketOptions.maxPrice
      spotOptions.spotInstanceType = description.instanceMarketOptionsRequest.spotMarketOptions.spotInstanceType
      instanceMarketOptionsRequest.spotOptions = spotOptions

      createLaunchConfigurationRequest.instanceMarketOptions = instanceMarketOptionsRequest
    }

    if (description.instanceTypes) {
      createLaunchConfigurationRequest.instanceTypes = description.instanceTypes
    }

    if (description.instanceTypesCheckPolicy) {
      createLaunchConfigurationRequest.instanceTypesCheckPolicy = description.instanceTypesCheckPolicy
    }

    def spinnakerTag = new InstanceTag(
      key: defaultServerGroupTagKey,
      value: description.serverGroupName
    )
    def instanceTags = [spinnakerTag]
    if (description.instanceTags) {
      instanceTags.addAll description.instanceTags.collect {
          new InstanceTag(key: it.key, value: it.value)
      }
    }
    createLaunchConfigurationRequest.instanceTags = instanceTags
    createLaunchConfigurationRequest
  }

  private static def buildAutoScalingGroupRequest(TencentDeployDescription description, String launchConfigurationId) {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest = new CreateAutoScalingGroupRequest()
    createAutoScalingGroupRequest.autoScalingGroupName = description.serverGroupName
    createAutoScalingGroupRequest.launchConfigurationId = launchConfigurationId
    createAutoScalingGroupRequest.desiredCapacity = description.desiredCapacity
    createAutoScalingGroupRequest.minSize = description.minSize
    createAutoScalingGroupRequest.maxSize = description.maxSize
    createAutoScalingGroupRequest.vpcId = description.vpcId

    if (description.subnetIds) {
      createAutoScalingGroupRequest.subnetIds = description.subnetIds
    }

    if (description.zones) {
      createAutoScalingGroupRequest.zones = description.zones
    }

    if (description.projectId) {
      createAutoScalingGroupRequest.projectId = description.projectId
    }

    if (description.retryPolicy) {
      createAutoScalingGroupRequest.retryPolicy = description.retryPolicy
    }

    if (description.zonesCheckPolicy) {
      createAutoScalingGroupRequest.zonesCheckPolicy = description.zonesCheckPolicy
    }

    if (description.defaultCooldown) {
      createAutoScalingGroupRequest.defaultCooldown = description.defaultCooldown
    }

    if (description.forwardLoadBalancers) {
      createAutoScalingGroupRequest.forwardLoadBalancers = description.forwardLoadBalancers.collect {
        def forwardLoadBalancer = new ForwardLoadBalancer()
        def targetAttributes = it.targetAttributes.collect {
          def target = new TargetAttribute()
          target.port = it.port
          target.weight = it.weight
          target
        }

        forwardLoadBalancer.targetAttributes = targetAttributes
        forwardLoadBalancer.listenerId = it.listenerId
        forwardLoadBalancer.loadBalancerId = it.loadBalancerId
        if (it.locationId) {
          forwardLoadBalancer.locationId = it.locationId
        }
        forwardLoadBalancer
      }
    }

    if (description.loadBalancerIds) {
      createAutoScalingGroupRequest.loadBalancerIds = description.loadBalancerIds
    }

    if (description.terminationPolicies) {
      createAutoScalingGroupRequest.terminationPolicies = description.terminationPolicies
    }

    createAutoScalingGroupRequest
  }

  List<AutoScalingGroup> getAllAutoScalingGroups() {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest()
      request.limit = DEFAULT_LIMIT
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request)
      response.autoScalingGroupSet
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<AutoScalingGroup> getAutoScalingGroupsByName(String name) {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest()
      request.limit = DEFAULT_LIMIT
      request.filters = [new Filter(name: 'auto-scaling-group-name', values: [name])]
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request)
      response.autoScalingGroupSet
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<LaunchConfiguration> getLaunchConfigurations(List<String> launchConfigurationIds) {
    try {
      Integer len = launchConfigurationIds.size()
      List<LaunchConfiguration> launchConfigurations = []
      def request = new DescribeLaunchConfigurationsRequest()
      request.limit = DEFAULT_LIMIT

      0.step len, DEFAULT_LIMIT, {
        Integer endIndex = Math.min(len, it+DEFAULT_LIMIT)
        request.launchConfigurationIds = launchConfigurationIds[it..endIndex-1]

        def response = client.DescribeLaunchConfigurations(request)
        launchConfigurations.addAll(response.launchConfigurationSet)
      }
      launchConfigurations
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getAutoScalingInstances(String asgId=null) {
    iterQuery { offset, limit ->
      def request = new DescribeAutoScalingInstancesRequest(offset: offset, limit: limit)
      if (asgId) {
        request.filters = [new Filter(name: 'auto-scaling-group-id', values: [asgId])]
      }
      def response = client.DescribeAutoScalingInstances request
      response.autoScalingInstanceSet
    } as List<Instance>
  }

  def getAutoScalingActivitiesByAsgId(String asgId, Integer maxActivityNum=100) {
    iterQuery(maxActivityNum, { offset, limit ->
      def request = new DescribeAutoScalingActivitiesRequest(offset: offset, limit: limit)
      request.filters = [new Filter(name: 'auto-scaling-group-id', values: [asgId])]
      def response = client.DescribeAutoScalingActivities request
      response.activitySet
    }) as List<Activity>
  }

  void resizeAutoScalingGroup(String asgId, def capacity) {
    try {
      def request = new ModifyAutoScalingGroupRequest()
      request.autoScalingGroupId = asgId
      request.maxSize = capacity.max
      request.minSize = capacity.min
      request.desiredCapacity = capacity.desired

      client.ModifyAutoScalingGroup(request)
    } catch(TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void enableAutoScalingGroup(String asgId) {
    try {
      def request = new EnableAutoScalingGroupRequest()
      request.autoScalingGroupId = asgId
      client.EnableAutoScalingGroup(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void disableAutoScalingGroup(String asgId) {
    try {
      def request = new DisableAutoScalingGroupRequest()
      request.autoScalingGroupId = asgId
      client.DisableAutoScalingGroup(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void deleteAutoScalingGroup(String asgId) {
    try {
      def request = new DeleteAutoScalingGroupRequest()
      request.autoScalingGroupId = asgId
      client.DeleteAutoScalingGroup(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void deleteLaunchConfiguration(String ascId) {
    try {
      def request = new DeleteLaunchConfigurationRequest()
      request.launchConfigurationId = ascId
      client.DeleteLaunchConfiguration(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def describeAutoScalingActivities(String asaId) {
    try {
      def request = new DescribeAutoScalingActivitiesRequest()
      request.activityIds = [asaId]
      def response = client.DescribeAutoScalingActivities(request)
      response
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def detachInstances(def asgId, def instanceIds) {
    try {
      def request = new DetachInstancesRequest()
      request.instanceIds = instanceIds
      request.autoScalingGroupId = asgId
      DetachInstancesResponse response = client.DetachInstances(request)
      response
    } catch(TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void removeInstances(def asgId, def instanceIds) {
    try {
      def request = new RemoveInstancesRequest()
      request.instanceIds = instanceIds
      request.autoScalingGroupId = asgId
      client.RemoveInstances(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void attachAutoScalingInstancesToForwardClb(def flb, def targets, boolean retry = false) {
    def retry_count = 0
    while (retry_count < DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME) {
      try {
        retry_count = retry_count + 1
        def request = new RegisterTargetsRequest()
        request.loadBalancerId = flb.loadBalancerId
        request.listenerId = flb.listenerId
        if (flb?.locationId) {
          request.locationId = flb?.locationId
        }
        request.targets = targets.collect {
          return new Target(
            instanceId: it.instanceId,
            weight: it.weight,
            port: it.port
          )
        }

        clbClient.RegisterTargets(request)
        break
      } catch (TencentCloudSDKException e) {
        if (e.toString().contains("FailedOperation") && retry) {
          log.info("lb service throw FailedOperation error, probably $flb.loadBalancerId is locked, will retry later.")
          sleep(500)
        } else {
          throw new TencentCloudSDKException(e.toString())
        }
      }
    }
  }

  void attachAutoScalingInstancesToClassicClb(def lbId, def targets) {
    try {
      def request = new RegisterTargetsWithClassicalLBRequest()
      request.loadBalancerId = lbId
      request.targets = targets.collect {
        return new ClassicalTargetInfo(
          instanceId: it.instanceId,
          weight: it.weight
        )
      }
      clbClient.RegisterTargetsWithClassicalLB(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void detachAutoScalingInstancesFromForwardClb(def flb, def targets, boolean retry = false) {
    def retry_count = 0
    while (retry_count < DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME) {
      try {
        retry_count = retry_count + 1
        def request = new DeregisterTargetsRequest()
        request.loadBalancerId = flb.loadBalancerId
        request.listenerId = flb.listenerId
        if (flb?.locationId) {
          request.locationId = flb?.locationId
        }
        request.targets = targets.collect {
          return new Target(
            instanceId: it.instanceId,
            weight: it.weight,
            port: it.port
          )
        }

        clbClient.DeregisterTargets(request)
        break
      } catch (TencentCloudSDKException e) {
        if (e.toString().contains("FailedOperation") && retry) {
          log.info("lb service throw FailedOperation error, probably $flb.loadBalancerId is locked, will retry later.")
          sleep(500)
        } else {
          throw new TencentCloudSDKException(e.toString())
        }      }
    }
  }

  void detachAutoScalingInstancesFromClassicClb(String lbId, List<String> instanceIds) {
    try {
      def request = new DeregisterTargetsFromClassicalLBRequest()
      request.loadBalancerId = lbId
      request.instanceIds = instanceIds
      clbClient.DeregisterTargetsFromClassicalLB(request)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<String> getClassicLbInstanceIds(String lbId) {
    try {
      def request = new DescribeClassicalLBTargetsRequest()
      request.loadBalancerId = lbId
      def response = clbClient.DescribeClassicalLBTargets(request)
      return response.targets.collect {
        it.instanceId
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getForwardLbTargets(def flb) {
    try {
      def request = new DescribeTargetsRequest()
      request.loadBalancerId = flb.loadBalancerId
      request.listenerIds = [flb.listenerId]
      def response = clbClient.DescribeTargets(request)
      return response.listeners
    } catch (TencentCloudSDKException e) {
      return []
    }
  }

  // scaling policy
  def createScalingPolicy(String asgId, UpsertTencentScalingPolicyDescription description) {
    try {
      def request = new CreateScalingPolicyRequest().with {
        autoScalingGroupId = asgId
        scalingPolicyName = description.serverGroupName + "-asp-" + new Date().time.toString()
        adjustmentType = description.adjustmentType
        adjustmentValue = description.adjustmentValue
        metricAlarm = description.metricAlarm
        cooldown = description.cooldown
        notificationUserGroupIds = description.notificationUserGroupIds
        it
      }
      def response = client.CreateScalingPolicy request
      response.autoScalingPolicyId
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def modifyScalingPolicy(String aspId, UpsertTencentScalingPolicyDescription description) {
    try {
      def request = new ModifyScalingPolicyRequest().with {
        autoScalingPolicyId = aspId
        adjustmentType = description.adjustmentType
        adjustmentValue = description.adjustmentValue
        metricAlarm = description.metricAlarm
        cooldown = description.cooldown
        notificationUserGroupIds = description.notificationUserGroupIds
        it
      }
      def response = client.ModifyScalingPolicy request
      response
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getScalingPolicies(String asgId=null) {
    iterQuery { offset, limit ->
      def request = new DescribeScalingPoliciesRequest(offset: offset, limit: limit)

      if (asgId) {
        request.filters = [new Filter(name: 'auto-scaling-group-id', values: [asgId])]
      }

      def response = client.DescribeScalingPolicies request
      response.scalingPolicySet
    } as List<ScalingPolicy>
  }

  def deleteScalingPolicy(String aspId) {
    try {
      def request = new DeleteScalingPolicyRequest().with {
        autoScalingPolicyId=aspId
        it
      }
      def response = client.DeleteScalingPolicy(request)
      response
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  // scheduled action
  def createScheduledAction(String asgId, UpsertTencentScheduledActionDescription description) {
    try {
      def request = new CreateScheduledActionRequest().with {
        autoScalingGroupId = asgId
        scheduledActionName = description.serverGroupName + "-asst-" + new Date().time.toString()
        maxSize = description.maxSize
        minSize = description.minSize
        desiredCapacity = description.desiredCapacity
        startTime = description.startTime
        endTime = description.endTime
        recurrence = description.recurrence
        it
      }
      def response = client.CreateScheduledAction request
      response.scheduledActionId
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def modifyScheduledAction(String asstId, UpsertTencentScheduledActionDescription description) {
    try {
      def request = new ModifyScheduledActionRequest().with {
        scheduledActionId = asstId
        maxSize = description.maxSize
        minSize = description.minSize
        desiredCapacity = description.desiredCapacity
        startTime = description.startTime
        endTime = description.endTime
        recurrence = description.recurrence
        it
      }
      def response = client.ModifyScheduledAction request
      response
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getScheduledAction(String asgId=null) {
    iterQuery { offset, limit ->
      def request = new DescribeScheduledActionsRequest(offset: offset, limit: limit)

      if (asgId) {
        request.filters = [new Filter(name: 'auto-scaling-group-id', values: [asgId])]
      }

      def response = client.DescribeScheduledActions request
      response.scheduledActionSet
    } as List<ScheduledAction>
  }

  def deleteScheduledAction(String asstId) {
    try {
      def request = new DeleteScheduledActionRequest().with {
        scheduledActionId = asstId
        it
      }
      def response = client.DeleteScheduledAction(request)
      response
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }
}
