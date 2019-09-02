package com.netflix.spinnaker.clouddriver.tencent.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class TencentDeployDescription extends AbstractTencentCredentialsDescription implements DeployDescription {
  /*
  common
   */
  String application
  String stack
  String detail
  String region
  String accountName
  String serverGroupName

  /*
  launch configuration part
   */
  String instanceType
  String imageId
  Integer projectId
  Map<String, Object> systemDisk
  List<Map<String, Object>> dataDisks
  Map<String, Object> internetAccessible
  Map<String, Object> loginSettings
  List<String> securityGroupIds
  Map<String, Object> enhancedService
  String userData
  String instanceChargeType
  Map<String, Object> instanceMarketOptionsRequest
  List<String> instanceTypes
  String instanceTypesCheckPolicy
  List<Map<String, String>> instanceTags

  /*
  auto scaling group part
   */
  Integer maxSize
  Integer minSize
  Integer desiredCapacity
  String vpcId
  Integer defaultCooldown
  List<String> loadBalancerIds
  List<Map<String, Object>> forwardLoadBalancers
  List<String> subnetIds
  List<String> terminationPolicies
  List<String> zones
  String retryPolicy
  String zonesCheckPolicy

  /*
  clone source
   */

  Source source = new Source()
  boolean copySourceScalingPoliciesAndActions = true


  @Canonical
  static class Source implements ServerGroupsNameable {
    String region
    String serverGroupName
    Boolean useSourceCapacity

    @Override
    Collection<String> getServerGroupNames() {
      return Collections.singletonList(serverGroupName)
    }
  }
}
