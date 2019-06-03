package com.netflix.spinnaker.clouddriver.tencent.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = "name")
class TencentInstance implements Instance, TencentBasicResource {

  final String cloudProvider = TencentCloudProvider.ID
  final String providerType = TencentCloudProvider.ID
  String instanceName
  String account
  String name //unique instance id
  Long launchTime
  String zone
  TencentInstanceHealth instanceHealth
  TencentTargetHealth targetHealth
  String vpcId
  String subnetId
  String instanceType
  String imageId
  List<String> securityGroupIds = []
  List<Map<String, String>> tags = []

  String serverGroupName

  @Override
  String getHumanReadableName() {
    return instanceName
  }

  @Override
  @JsonIgnore
  String getMonikerName() {
    return serverGroupName
  }

  List<Map<String, Object>> getHealth() {
    ObjectMapper objectMapper = new ObjectMapper()
    def healths = []
    if (instanceHealth) {
      healths << objectMapper.convertValue(instanceHealth, new TypeReference<Map<String, Object>>() {})
    }
    if (targetHealth) {
      healths << objectMapper.convertValue(targetHealth, new TypeReference<Map<String, Object>>() {})
    }
    return healths as List<Map<String, Object>>
  }

  @Override
  HealthState getHealthState() {
    someUpRemainingUnknown(health) ? HealthState.Up :
      anyStarting(health) ? HealthState.Starting :
        anyDown(health) ? HealthState.Down :
          anyOutOfService(health) ? HealthState.OutOfService : HealthState.Unknown
  }

  Moniker getMoniker() {
    return NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(account)
      .withResource(TencentBasicResource)
      .deriveMoniker(this)
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthList) {
    List<Map<String, String>> knownHealthList = healthList?.findAll {
      it.state != HealthState.Unknown.toString()
    }
    knownHealthList ? knownHealthList.every {
      it.state == HealthState.Up.toString()
    } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthList) {
    healthList.any {
      it.state == HealthState.Starting.toString()
    }
  }

  private static boolean anyDown(List<Map<String, String>> healthList) {
    healthList.any {
      it.state == HealthState.Down.toString()
    }
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthList) {
    healthList.any {
      it.state == HealthState.OutOfService.toString()
    }
  }
}
