package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.moniker.Moniker

class TencentServerGroup implements ServerGroup, TencentBasicResource {
  final String type = TencentCloudProvider.ID
  final String cloudProvider = TencentCloudProvider.ID
  String accountName
  String name
  String region
  Set<String> zones
  Set<TencentInstance> instances = []
  Map<String, Object> image = [:]
  Map<String, Object> launchConfig = [:]
  Map<String, Object> asg = [:]
  Map buildInfo
  String vpcId

  List<Map> scalingPolicies
  List<Map> scheduledActions

  Boolean disabled = false

  @Override
  Moniker getMoniker() {
    return NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(accountName)
      .withResource(TencentBasicResource)
      .deriveMoniker(this)
  }

  @Override
  String getMonikerName() {
    name
  }

  @Override
  Boolean isDisabled() {
    disabled
  }

  @Override
  Long getCreatedTime() {
    def dateTime = AutoScalingClient.ConvertIsoDateTime(asg.createdTime as String)
    dateTime ? dateTime.time : null
  }

  @Override
  Set<String> getLoadBalancers() {
    def loadBalancerNames = []
    if (asg && asg.containsKey("forwardLoadBalancerSet")) {
      loadBalancerNames.addAll(asg.forwardLoadBalancerSet.collect {
        it["loadBalancerId"]
      })
    }

    if (asg && asg.containsKey("loadBalancerIdSet")) {
      loadBalancerNames.addAll(asg.loadBalancerIdSet)
    }

    return loadBalancerNames as Set<String>
  }

  @Override
  Set<String> getSecurityGroups() {
    def securityGroups = []
    if (launchConfig && launchConfig.containsKey("securityGroupIds")) {
      securityGroups = launchConfig.securityGroupIds
    }
    securityGroups as Set<String>
  }

  @Override
  InstanceCounts getInstanceCounts() {
    new InstanceCounts(
      total: instances?.size() ?: 0,
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
    )
  }

  @Override
  Capacity getCapacity() {
    asg ? new Capacity(
      min: asg.minSize ? asg.minSize as Integer : 0,
      max: asg.maxSize ? asg.maxSize as Integer : 0,
      desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0
    ) : null
  }

  @Override
  ImagesSummary getImagesSummary() {
    def buildInfo = buildInfo
    def image =image
    return new ImagesSummary() {
      @Override
      List<ImageSummary> getSummaries() {
        return [new ImageSummary() {
          String serverGroupName = name
          String imageName = image?.name
          String imageId = image?.imageId

          @Override
          Map<String, Object> getBuildInfo() {
            return buildInfo
          }

          @Override
          Map<String, Object> getImage() {
            return image
          }
        }]
      }
    }
  }

  @Override
  ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Collection<Instance> filterInstancesByHealthState(Collection<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
