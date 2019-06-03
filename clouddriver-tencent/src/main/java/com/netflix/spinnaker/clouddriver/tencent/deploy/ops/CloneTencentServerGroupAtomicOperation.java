package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.handlers.TencentDeployHandler;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentServerGroup;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Data
@Slf4j
public class CloneTencentServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  public CloneTencentServerGroupAtomicOperation(TencentDeployDescription description) {
    this.description = description;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    TencentDeployDescription newDescription = cloneAndOverrideDescription();
    DeploymentResult result = tencentDeployHandler.handle(newDescription, priorOutputs);
    return result;
  }

  private TencentDeployDescription cloneAndOverrideDescription() {
    TencentDeployDescription newDescription = SerializationUtils.clone(this.description);

    if (description == null
        || description.getSource() == null
        || StringUtils.isEmpty(description.getSource().getRegion())
        || StringUtils.isEmpty(description.getSource().getServerGroupName())) {
      return newDescription;
    }

    String sourceServerGroupName = description.getSource().getServerGroupName();
    String sourceRegion = description.getSource().getRegion();
    String accountName = description.getAccountName();
    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing copy of server group " + sourceServerGroupName + "...");

    // look up source server group
    TencentServerGroup sourceServerGroup =
        tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName);

    if (sourceServerGroup == null) {
      return newDescription;
    }

    // start override source description
    final String region = description.getRegion();
    newDescription.setRegion(!StringUtils.isEmpty(region) ? region : sourceRegion);
    final String application = description.getApplication();
    newDescription.setApplication(
        !StringUtils.isEmpty(application) ? application : sourceServerGroup.getMoniker().getApp());
    final String stack = description.getStack();
    newDescription.setStack(
        !StringUtils.isEmpty(stack) ? stack : sourceServerGroup.getMoniker().getStack());

    Map<String, Object> sourceLaunchConfig = sourceServerGroup.getLaunchConfig();
    if (!CollectionUtils.isEmpty(sourceLaunchConfig)) {
      final String type = description.getInstanceType();
      newDescription.setInstanceType(
          !StringUtils.isEmpty(type) ? type : (String) sourceLaunchConfig.get("instanceType"));
      final String imageId = description.getImageId();
      newDescription.setImageId(
          !StringUtils.isEmpty(imageId) ? imageId : (String) sourceLaunchConfig.get("imageId"));
      final Integer projectId = description.getProjectId();
      newDescription.setProjectId(
          projectId != null ? projectId : (Integer) sourceLaunchConfig.get("projectId"));
      final Map<String, Object> sysDisk = description.getSystemDisk();
      newDescription.setSystemDisk(
          !CollectionUtils.isEmpty(sysDisk)
              ? sysDisk
              : (Map<String, Object>) sourceLaunchConfig.get("systemDisk"));
      final List<Map<String, Object>> dataDisks = description.getDataDisks();
      newDescription.setDataDisks(
          !CollectionUtils.isEmpty(dataDisks)
              ? dataDisks
              : (List<Map<String, Object>>) sourceLaunchConfig.get("dataDisks"));
      final Map<String, Object> accessible = description.getInternetAccessible();
      newDescription.setInternetAccessible(
          !CollectionUtils.isEmpty(accessible)
              ? accessible
              : (Map<String, Object>) sourceLaunchConfig.get("internetAccessible"));
      final Map<String, Object> loginSettings = description.getLoginSettings();
      newDescription.setLoginSettings(
          !CollectionUtils.isEmpty(loginSettings)
              ? loginSettings
              : (Map<String, Object>) sourceLaunchConfig.get("loginSettings"));
      final List<String> securityGroupIds = description.getSecurityGroupIds();
      newDescription.setSecurityGroupIds(
          !CollectionUtils.isEmpty(securityGroupIds)
              ? securityGroupIds
              : (List<String>) sourceLaunchConfig.get("securityGroupIds"));
      final Map<String, Object> enhancedService = description.getEnhancedService();
      newDescription.setEnhancedService(
          !CollectionUtils.isEmpty(enhancedService)
              ? enhancedService
              : (Map<String, Object>) sourceLaunchConfig.get("enhancedService"));
      final String userData = description.getUserData();
      newDescription.setUserData(
          !StringUtils.isEmpty(userData) ? userData : (String) sourceLaunchConfig.get("userData"));
      final String instanceChargeType = description.getInstanceChargeType();
      newDescription.setInstanceChargeType(
          !StringUtils.isEmpty(instanceChargeType)
              ? instanceChargeType
              : (String) sourceLaunchConfig.get("instanceChargeType"));
      // newDescription.instanceTypes = description.instanceTypes ?:
      // sourceLaunchConfig.instanceTypes as List
      final Map<String, Object> instanceMarketOptionsRequest =
          description.getInstanceMarketOptionsRequest();
      newDescription.setInstanceMarketOptionsRequest(
          !CollectionUtils.isEmpty(instanceMarketOptionsRequest)
              ? instanceMarketOptionsRequest
              : (Map<String, Object>) sourceLaunchConfig.get("instanceMarketOptionsRequest"));
      final String instanceTypesCheckPolicy = description.getInstanceTypesCheckPolicy();
      newDescription.setInstanceTypesCheckPolicy(
          !StringUtils.isEmpty(instanceTypesCheckPolicy)
              ? instanceTypesCheckPolicy
              : (String) sourceLaunchConfig.get("instanceTypesCheckPolicy"));
    }

    if (!CollectionUtils.isEmpty(description.getInstanceTags())) {
      newDescription.setInstanceTags(description.getInstanceTags());
    } else if (!CollectionUtils.isEmpty((Collection<?>) sourceLaunchConfig.get("instanceTags"))) {
      List<Map<String, String>> cloneInstanceTags = new ArrayList<>();
      for (Map<String, String> tag :
          (List<Map<String, String>>) sourceLaunchConfig.get("instanceTags")) {
        if (!tag.get("key").equals(AutoScalingClient.getDefaultServerGroupTagKey())) {
          cloneInstanceTags.add(tag);
        }
        newDescription.setInstanceTags(cloneInstanceTags);
      }
    }
    Map<String, Object> sourceAutoScalingGroup = sourceServerGroup.getAsg();
    if (!CollectionUtils.isEmpty(sourceAutoScalingGroup)) {
      final Integer maxSize = description.getMaxSize();
      newDescription.setMaxSize(
          maxSize != null ? maxSize : (Integer) sourceAutoScalingGroup.get("maxSize"));
      final Integer minSize = description.getMinSize();
      newDescription.setMinSize(
          minSize != null ? minSize : (Integer) sourceAutoScalingGroup.get("minSize"));
      final Integer desiredCapacity = description.getDesiredCapacity();
      newDescription.setDesiredCapacity(
          desiredCapacity != null
              ? desiredCapacity
              : (Integer) sourceAutoScalingGroup.get("desiredCapacity"));
      final String vpcId = description.getVpcId();
      newDescription.setVpcId(
          !StringUtils.isEmpty(vpcId) ? vpcId : (String) sourceAutoScalingGroup.get("vpcId"));

      if (!StringUtils.isEmpty(newDescription.getVpcId())) {
        final List<String> ids = description.getSubnetIds();
        newDescription.setSubnetIds(
            !CollectionUtils.isEmpty(ids)
                ? ids
                : (List<String>) sourceAutoScalingGroup.get("subnetIdSet"));
      } else {
        final List<String> zones = description.getZones();
        newDescription.setZones(
            !CollectionUtils.isEmpty(zones)
                ? zones
                : (List<String>) sourceAutoScalingGroup.get("zoneSet"));
      }

      final Integer cooldown = description.getDefaultCooldown();
      newDescription.setDefaultCooldown(
          cooldown != null ? cooldown : (Integer) sourceAutoScalingGroup.get("defaultCooldown"));
      final List<String> policies = description.getTerminationPolicies();
      newDescription.setTerminationPolicies(
          !CollectionUtils.isEmpty(policies)
              ? policies
              : (List<String>) sourceAutoScalingGroup.get("terminationPolicies"));
      final List<String> ids = description.getLoadBalancerIds();
      newDescription.setLoadBalancerIds(
          !CollectionUtils.isEmpty(ids)
              ? ids
              : (List<String>) sourceAutoScalingGroup.get("loadBalancerIds"));
      final List<Map<String, Object>> balancers = description.getForwardLoadBalancers();
      newDescription.setForwardLoadBalancers(
          !CollectionUtils.isEmpty(balancers)
              ? balancers
              : (List<Map<String, Object>>) sourceAutoScalingGroup.get("forwardLoadBalancerSet"));
      final String retryPolicy = description.getRetryPolicy();
      newDescription.setRetryPolicy(
          !StringUtils.isEmpty(retryPolicy)
              ? retryPolicy
              : (String) sourceAutoScalingGroup.get("retryPolicy"));
      final String zonesCheckPolicy = description.getZonesCheckPolicy();
      newDescription.setZonesCheckPolicy(
          !StringUtils.isEmpty(zonesCheckPolicy)
              ? zonesCheckPolicy
              : (String) sourceAutoScalingGroup.get("zoneCheckPolicy"));
    }

    return newDescription;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final String BASE_PHASE = "CLONE_SERVER_GROUP";
  private TencentDeployDescription description;
  @Autowired private TencentClusterProvider tencentClusterProvider;
  @Autowired private TencentDeployHandler tencentDeployHandler;
}
