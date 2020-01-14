package com.netflix.spinnaker.clouddriver.tencent.client;

import static java.lang.Thread.sleep;

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.tencentcloudapi.as.v20180419.AsClient;
import com.tencentcloudapi.as.v20180419.models.*;
import com.tencentcloudapi.clb.v20180317.ClbClient;
import com.tencentcloudapi.clb.v20180317.models.*;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class AutoScalingClient extends AbstractTencentServiceClient {
  public AutoScalingClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey);

    client = new AsClient(getCred(), region, getClientProfile());

    String clbEndPoint = "clb.tencentcloudapi.com";
    HttpProfile clbHttpProfile = new HttpProfile();
    clbHttpProfile.setEndpoint(clbEndPoint);

    ClientProfile clbClientProfile = new ClientProfile();
    clbClientProfile.setHttpProfile(clbHttpProfile);

    clbClient = new ClbClient(getCred(), region, clbClientProfile);
  }

  public String deploy(TencentDeployDescription description) {
    try {
      // 1. create launch configuration
      CreateLaunchConfigurationRequest createLaunchConfigurationRequest =
          buildLaunchConfigurationRequest(description);
      CreateLaunchConfigurationResponse createLaunchConfigurationResponse =
          client.CreateLaunchConfiguration(createLaunchConfigurationRequest);
      String launchConfigurationId = createLaunchConfigurationResponse.getLaunchConfigurationId();

      try {
        // 2. create auto scaling group
        CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
            buildAutoScalingGroupRequest(description, launchConfigurationId);
        CreateAutoScalingGroupResponse createAutoScalingGroupResponse =
            client.CreateAutoScalingGroup(createAutoScalingGroupRequest);
        return createAutoScalingGroupResponse.getAutoScalingGroupId();
      } catch (TencentCloudSDKException e) {
        // if create auto scaling group failed, delete launch configuration.
        sleep(5000);
        log.error(e.toString());
        DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest();
        request.setLaunchConfigurationId(launchConfigurationId);
        client.DeleteLaunchConfiguration(request);
        throw e;
      }

    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  private static CreateLaunchConfigurationRequest buildLaunchConfigurationRequest(
      TencentDeployDescription description) {
    CreateLaunchConfigurationRequest createLaunchConfigurationRequest =
        new CreateLaunchConfigurationRequest();

    String launchConfigurationName = description.getServerGroupName();
    createLaunchConfigurationRequest.setLaunchConfigurationName(launchConfigurationName);
    createLaunchConfigurationRequest.setImageId(description.getImageId());

    if (description.getProjectId() != null) {
      createLaunchConfigurationRequest.setProjectId(description.getProjectId());
    }

    if (description.getInstanceType() != null) {
      createLaunchConfigurationRequest.setInstanceType(description.getInstanceType());
    }

    if (description.getSystemDisk() != null) {
      SystemDisk systemDisk = new SystemDisk();
      systemDisk.setDiskSize((Integer) description.getSystemDisk().get("diskSize"));
      systemDisk.setDiskType((String) description.getSystemDisk().get("diskType"));
      createLaunchConfigurationRequest.setSystemDisk(systemDisk);
    }

    if (description.getDataDisks() != null && description.getDataDisks().size() > 0) {
      createLaunchConfigurationRequest.setDataDisks(
          description.getDataDisks().stream()
              .map(
                  (it) -> {
                    DataDisk dataDisk = new DataDisk();
                    dataDisk.setDiskType((String) it.get("diskType"));
                    dataDisk.setDiskSize((Integer) it.get("diskSize"));
                    dataDisk.setSnapshotId((String) it.get("snapShotId"));
                    return dataDisk;
                  })
              .toArray(DataDisk[]::new));
    }

    if (description.getInternetAccessible() != null
        && description.getInternetAccessible().size() > 0) {
      InternetAccessible internetAccessible = new InternetAccessible();
      internetAccessible.setInternetChargeType(
          (String) description.getInternetAccessible().get("internetChargeType"));
      internetAccessible.setInternetMaxBandwidthOut(
          (Integer) description.getInternetAccessible().get("internetMaxBandwidthOut"));
      internetAccessible.setPublicIpAssigned(
          (Boolean) description.getInternetAccessible().get("publicIpAssigned"));
      createLaunchConfigurationRequest.setInternetAccessible(internetAccessible);
    }

    if (description.getLoginSettings() != null && description.getLoginSettings().size() > 0) {
      LoginSettings loginSettings = new LoginSettings();
      loginSettings.setKeepImageLogin(
          (Boolean) description.getLoginSettings().get("keepImageLogin"));
      loginSettings.setKeyIds((String[]) description.getLoginSettings().get("keyIds"));
      loginSettings.setPassword((String) description.getLoginSettings().get("password"));
      createLaunchConfigurationRequest.setLoginSettings(loginSettings);
    }

    if (description.getSecurityGroupIds() != null && description.getSecurityGroupIds().size() > 0) {
      createLaunchConfigurationRequest.setSecurityGroupIds(
          description.getSecurityGroupIds().stream().toArray(String[]::new));
    }

    if (description.getEnhancedService() != null) {
      EnhancedService enhancedService = new EnhancedService();
      enhancedService.setMonitorService(new RunMonitorServiceEnabled());
      enhancedService
          .getMonitorService()
          .setEnabled(
              (Boolean)
                  ((Map<String, Object>) description.getEnhancedService().get("monitorService"))
                      .get("enabled"));

      enhancedService.setSecurityService(new RunSecurityServiceEnabled());
      enhancedService
          .getSecurityService()
          .setEnabled(
              (Boolean)
                  ((Map<String, Object>) description.getEnhancedService().get("securityService"))
                      .get("enabled"));

      createLaunchConfigurationRequest.setEnhancedService(enhancedService);
    }

    if (!StringUtils.isEmpty(description.getUserData())) {
      createLaunchConfigurationRequest.setUserData(description.getUserData());
    }

    if (!StringUtils.isEmpty(description.getInstanceChargeType())) {
      createLaunchConfigurationRequest.setInstanceChargeType(description.getInstanceChargeType());
    }

    if (description.getInstanceMarketOptionsRequest() != null
        && description.getInstanceMarketOptionsRequest().size() > 0) {
      InstanceMarketOptionsRequest instanceMarketOptionsRequest =
          new InstanceMarketOptionsRequest();
      instanceMarketOptionsRequest.setMarketType(
          (String) description.getInstanceMarketOptionsRequest().get("marketType"));

      SpotMarketOptions spotOptions = new SpotMarketOptions();
      spotOptions.setMaxPrice(
          (String)
              ((Map) description.getInstanceMarketOptionsRequest().get("spotMarketOptions"))
                  .get("maxPrice"));
      spotOptions.setSpotInstanceType(
          (String)
              ((Map) description.getInstanceMarketOptionsRequest().get("spotMarketOptions"))
                  .get("spotInstanceType"));
      instanceMarketOptionsRequest.setSpotOptions(spotOptions);

      createLaunchConfigurationRequest.setInstanceMarketOptions(instanceMarketOptionsRequest);
    }

    if (!CollectionUtils.isEmpty(description.getInstanceTypes())) {
      createLaunchConfigurationRequest.setInstanceTypes(
          description.getInstanceTypes().stream().toArray(String[]::new));
    }

    if (!StringUtils.isEmpty(description.getInstanceTypesCheckPolicy())) {
      createLaunchConfigurationRequest.setInstanceTypesCheckPolicy(
          description.getInstanceTypesCheckPolicy());
    }

    InstanceTag spinnakerTag = new InstanceTag();
    spinnakerTag.setKey(defaultServerGroupTagKey);
    spinnakerTag.setValue(description.getServerGroupName());

    List<InstanceTag> instanceTags = Stream.of(spinnakerTag).collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(description.getInstanceTags())) {
      instanceTags.addAll(
          description.getInstanceTags().stream()
              .map(
                  (it) -> {
                    InstanceTag tag = new InstanceTag();
                    tag.setKey(it.get("key"));
                    tag.setKey(it.get("value"));
                    return tag;
                  })
              .collect(Collectors.toList()));
    }

    createLaunchConfigurationRequest.setInstanceTags(
        instanceTags.stream().toArray(InstanceTag[]::new));
    return createLaunchConfigurationRequest;
  }

  private static CreateAutoScalingGroupRequest buildAutoScalingGroupRequest(
      TencentDeployDescription description, String launchConfigurationId) {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        new CreateAutoScalingGroupRequest();
    createAutoScalingGroupRequest.setAutoScalingGroupName(description.getServerGroupName());
    createAutoScalingGroupRequest.setLaunchConfigurationId(launchConfigurationId);
    createAutoScalingGroupRequest.setDesiredCapacity(description.getDesiredCapacity());
    createAutoScalingGroupRequest.setMinSize(description.getMinSize());
    createAutoScalingGroupRequest.setMaxSize(description.getMaxSize());
    createAutoScalingGroupRequest.setVpcId(description.getVpcId());

    if (!CollectionUtils.isEmpty(description.getSubnetIds())) {
      createAutoScalingGroupRequest.setSubnetIds(
          description.getSubnetIds().stream().toArray(String[]::new));
    }

    if (!CollectionUtils.isEmpty(description.getZones())) {
      createAutoScalingGroupRequest.setZones(
          description.getZones().stream().toArray(String[]::new));
    }

    if (description.getProjectId() != null) {
      createAutoScalingGroupRequest.setProjectId(description.getProjectId());
    }

    if (!StringUtils.isEmpty(description.getRetryPolicy())) {
      createAutoScalingGroupRequest.setRetryPolicy(description.getRetryPolicy());
    }

    if (!StringUtils.isEmpty(description.getZonesCheckPolicy())) {
      createAutoScalingGroupRequest.setZonesCheckPolicy(description.getZonesCheckPolicy());
    }

    if (description.getDefaultCooldown() != null) {
      createAutoScalingGroupRequest.setDefaultCooldown(description.getDefaultCooldown());
    }

    if (!CollectionUtils.isEmpty(description.getForwardLoadBalancers())) {
      createAutoScalingGroupRequest.setForwardLoadBalancers(
          description.getForwardLoadBalancers().stream()
              .map(
                  it -> {
                    ForwardLoadBalancer forwardLoadBalancer = new ForwardLoadBalancer();
                    TargetAttribute[] targetAttributes =
                        ((List<Map>) it.get("targetAttributes"))
                            .stream()
                                .map(
                                    attr -> {
                                      TargetAttribute target = new TargetAttribute();
                                      target.setPort((Integer) attr.get("port"));
                                      target.setWeight((Integer) attr.get("weight"));
                                      return target;
                                    })
                                .toArray(TargetAttribute[]::new);

                    forwardLoadBalancer.setTargetAttributes(targetAttributes);
                    forwardLoadBalancer.setListenerId((String) it.get("listenerId"));
                    forwardLoadBalancer.setLoadBalancerId((String) it.get("loadBalancerId"));
                    if (it.containsKey("locationId")) {
                      forwardLoadBalancer.setLocationId((String) it.get("locationId"));
                    }
                    return forwardLoadBalancer;
                  })
              .toArray(ForwardLoadBalancer[]::new));
    }

    if (!CollectionUtils.isEmpty(description.getLoadBalancerIds())) {
      createAutoScalingGroupRequest.setLoadBalancerIds(
          description.getLoadBalancerIds().stream().toArray(String[]::new));
    }

    if (!CollectionUtils.isEmpty(description.getTerminationPolicies())) {
      createAutoScalingGroupRequest.setTerminationPolicies(
          description.getTerminationPolicies().stream().toArray(String[]::new));
    }

    return createAutoScalingGroupRequest;
  }

  public List<AutoScalingGroup> getAllAutoScalingGroups() {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
      request.setLimit(getDEFAULT_LIMIT());
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request);
      return Arrays.stream(response.getAutoScalingGroupSet()).collect(Collectors.toList());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<AutoScalingGroup> getAutoScalingGroupsByName(String name) {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
      request.setLimit(getDEFAULT_LIMIT());
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-name");
      filter.setValues(new String[] {name});
      request.setFilters(new Filter[] {filter});
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request);
      return Arrays.stream(response.getAutoScalingGroupSet()).collect(Collectors.toList());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<LaunchConfiguration> getLaunchConfigurations(
      final List<String> launchConfigurationIds) {
    try {
      final int len = launchConfigurationIds.size();
      final List<LaunchConfiguration> launchConfigurations = new ArrayList<LaunchConfiguration>();
      final DescribeLaunchConfigurationsRequest request = new DescribeLaunchConfigurationsRequest();
      request.setLimit(getDEFAULT_LIMIT());

      for (int i = 0; i < len; i += getDEFAULT_LIMIT()) {
        int endIndex = Math.min(len, i + getDEFAULT_LIMIT());
        request.setLaunchConfigurationIds(
            launchConfigurationIds.stream().skip(i).limit(endIndex - i).toArray(String[]::new));

        DescribeLaunchConfigurationsResponse response =
            client.DescribeLaunchConfigurations(request);
        launchConfigurations.addAll(Arrays.asList(response.getLaunchConfigurationSet()));
      }
      return launchConfigurations;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<Instance> getAutoScalingInstances(String asgId) {
    return iterQuery(
        (offset, limit) -> {
          DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          if (!StringUtils.isEmpty(asgId)) {
            Filter filter = new Filter();
            filter.setName("auto-scaling-group-id");
            filter.setValues(new String[] {asgId});
            request.setFilters(new Filter[] {filter});
          }
          DescribeAutoScalingInstancesResponse response =
              client.DescribeAutoScalingInstances(request);
          return Arrays.asList(response.getAutoScalingInstanceSet());
        });
  }

  public List<Instance> getAutoScalingInstances() {
    return getAutoScalingInstances(null);
  }

  public List<Activity> getAutoScalingActivitiesByAsgId(String asgId, Integer maxActivityNum) {
    return iterQuery(
        maxActivityNum,
        (offset, limit) -> {
          DescribeAutoScalingActivitiesRequest request = new DescribeAutoScalingActivitiesRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          Filter filter = new Filter();
          filter.setName("auto-scaling-group-id");
          filter.setValues(new String[] {asgId});
          request.setFilters(new Filter[] {filter});
          DescribeAutoScalingActivitiesResponse response =
              client.DescribeAutoScalingActivities(request);
          return Arrays.asList(response.getActivitySet());
        });
  }

  public List<Activity> getAutoScalingActivitiesByAsgId(String asgId) {
    return getAutoScalingActivitiesByAsgId(asgId, 100);
  }

  public void resizeAutoScalingGroup(
      String asgId, ResizeTencentServerGroupDescription.Capacity capacity) {
    try {
      ModifyAutoScalingGroupRequest request = new ModifyAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      request.setMaxSize(capacity.getMax());
      request.setMinSize(capacity.getMin());
      request.setDesiredCapacity(capacity.getDesired());

      client.ModifyAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void enableAutoScalingGroup(String asgId) {
    try {
      EnableAutoScalingGroupRequest request = new EnableAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.EnableAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void disableAutoScalingGroup(String asgId) {
    try {
      DisableAutoScalingGroupRequest request = new DisableAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.DisableAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void deleteAutoScalingGroup(String asgId) {
    try {
      DeleteAutoScalingGroupRequest request = new DeleteAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.DeleteAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void deleteLaunchConfiguration(String ascId) {
    try {
      DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest();
      request.setLaunchConfigurationId(ascId);
      client.DeleteLaunchConfiguration(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public DescribeAutoScalingActivitiesResponse describeAutoScalingActivities(String asaId) {
    try {
      DescribeAutoScalingActivitiesRequest request = new DescribeAutoScalingActivitiesRequest();
      request.setActivityIds(new String[] {asaId});
      return client.DescribeAutoScalingActivities(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public DetachInstancesResponse detachInstances(String asgId, List<String> instanceIds) {
    try {
      DetachInstancesRequest request = new DetachInstancesRequest();
      request.setInstanceIds(instanceIds.stream().toArray(String[]::new));
      request.setAutoScalingGroupId(asgId);
      DetachInstancesResponse response = client.DetachInstances(request);
      return response;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void removeInstances(String asgId, List<String> instanceIds) {
    try {
      RemoveInstancesRequest request = new RemoveInstancesRequest();
      request.setInstanceIds(instanceIds.stream().toArray(String[]::new));
      request.setAutoScalingGroupId(asgId);
      client.RemoveInstances(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void attachAutoScalingInstancesToForwardClb(
      ForwardLoadBalancer flb, List<? extends Target> targets) throws TencentCloudSDKException {
    attachAutoScalingInstancesToForwardClb(flb, targets, false);
  }

  public void attachAutoScalingInstancesToForwardClb(
      ForwardLoadBalancer flb, List<? extends Target> targets, boolean retry)
      throws TencentCloudSDKException {
    int retry_count = 0;
    while (retry_count < DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME) {
      try {
        retry_count += 1;
        RegisterTargetsRequest request = new RegisterTargetsRequest();
        request.setLoadBalancerId(flb.getLoadBalancerId());
        request.setListenerId(flb.getListenerId());
        request.setLocationId((flb == null ? null : flb.getLocationId()));
        request.setTargets(
            targets.stream()
                .map(
                    it -> {
                      Target target = new Target();
                      target.setInstanceId(it.getInstanceId());
                      target.setWeight(it.getWeight());
                      target.setPort(it.getPort());
                      return target;
                    })
                .toArray(Target[]::new));
        clbClient.RegisterTargets(request);
        break;
      } catch (TencentCloudSDKException e) {
        if (e.toString().contains("FailedOperation") && retry) {
          log.info(
              "lb service throw FailedOperation error, probably $flb.loadBalancerId is locked, will retry later.");
          try {
            sleep(500);
          } catch (InterruptedException ex) {
            ex.printStackTrace();
            throw new TencentCloudSDKException(ex.toString());
          }
        } else {
          throw new TencentCloudSDKException(e.toString());
        }
      }
    }
  }

  public void attachAutoScalingInstancesToClassicClb(String lbId, List<? extends Target> targets) {
    try {
      RegisterTargetsWithClassicalLBRequest request = new RegisterTargetsWithClassicalLBRequest();
      request.setLoadBalancerId(lbId);
      request.setTargets(
          targets.stream()
              .map(
                  it -> {
                    ClassicalTargetInfo target = new ClassicalTargetInfo();
                    target.setInstanceId(it.getInstanceId());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(ClassicalTargetInfo[]::new));

      clbClient.RegisterTargetsWithClassicalLB(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void detachAutoScalingInstancesFromForwardClb(
      ForwardLoadBalancer flb, List<? extends Target> targets) throws TencentCloudSDKException {
    detachAutoScalingInstancesFromForwardClb(flb, targets, false);
  }

  public void detachAutoScalingInstancesFromForwardClb(
      ForwardLoadBalancer flb, List<? extends Target> targets, boolean retry)
      throws TencentCloudSDKException {
    int retry_count = 0;
    while (retry_count < DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME) {
      try {
        retry_count += 1;
        DeregisterTargetsRequest request = new DeregisterTargetsRequest();
        request.setLoadBalancerId(flb.getLoadBalancerId());
        request.setListenerId(flb.getListenerId());
        request.setLocationId(flb.getLocationId());
        request.setTargets(
            targets.stream()
                .map(
                    it -> {
                      Target target = new Target();
                      target.setInstanceId(it.getInstanceId());
                      target.setWeight(it.getWeight());
                      target.setPort(it.getPort());
                      return target;
                    })
                .toArray(Target[]::new));
        clbClient.DeregisterTargets(request);
        break;
      } catch (TencentCloudSDKException e) {
        if (e.toString().contains("FailedOperation") && retry) {
          log.info(
              "lb service throw FailedOperation error, probably $flb.loadBalancerId is locked, will retry later.");
          try {
            sleep(500);
          } catch (InterruptedException ex) {
            ex.printStackTrace();
            throw new TencentCloudSDKException(ex.toString());
          }
        } else {
          throw new TencentCloudSDKException(e.toString());
        }
      }
    }
  }

  public void detachAutoScalingInstancesFromClassicClb(String lbId, List<String> instanceIds) {
    try {
      DeregisterTargetsFromClassicalLBRequest request =
          new DeregisterTargetsFromClassicalLBRequest();
      request.setLoadBalancerId(lbId);
      request.setInstanceIds(instanceIds.stream().toArray(String[]::new));
      clbClient.DeregisterTargetsFromClassicalLB(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<String> getClassicLbInstanceIds(String lbId) {
    try {
      DescribeClassicalLBTargetsRequest request = new DescribeClassicalLBTargetsRequest();
      request.setLoadBalancerId(lbId);
      DescribeClassicalLBTargetsResponse response = clbClient.DescribeClassicalLBTargets(request);
      return Arrays.stream(response.getTargets())
          .map(
              it -> {
                return it.getInstanceId();
              })
          .collect(Collectors.toList());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<ListenerBackend> getForwardLbTargets(ForwardLoadBalancer flb) {
    try {
      DescribeTargetsRequest request = new DescribeTargetsRequest();
      request.setLoadBalancerId(flb.getLoadBalancerId());
      request.setListenerIds(new String[] {flb.getListenerId()});
      DescribeTargetsResponse response = clbClient.DescribeTargets(request);
      return Arrays.asList(response.getListeners());
    } catch (TencentCloudSDKException e) {
      return new ArrayList<ListenerBackend>();
    }
  }

  public String createScalingPolicy(
      final String asgId, final UpsertTencentScalingPolicyDescription description) {
    try {
      CreateScalingPolicyRequest request = new CreateScalingPolicyRequest();
      request.setAutoScalingGroupId(asgId);
      request.setScalingPolicyName(
          description.getServerGroupName() + "-asp-" + new Date().getTime());
      request.setAdjustmentType(description.getAdjustmentType());
      request.setAdjustmentValue(description.getAdjustmentValue());
      request.setMetricAlarm(description.getMetricAlarm());
      request.setCooldown(description.getCooldown());
      request.setNotificationUserGroupIds(
          description.getNotificationUserGroupIds().stream().toArray(String[]::new));

      CreateScalingPolicyResponse response = client.CreateScalingPolicy(request);
      return response.getAutoScalingPolicyId();
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public ModifyScalingPolicyResponse modifyScalingPolicy(
      final String aspId, final UpsertTencentScalingPolicyDescription description) {
    try {
      ModifyScalingPolicyRequest request = new ModifyScalingPolicyRequest();
      request.setAutoScalingPolicyId(aspId);
      request.setAdjustmentType(description.getAdjustmentType());
      request.setAdjustmentValue(description.getAdjustmentValue());
      request.setMetricAlarm(description.getMetricAlarm());
      request.setCooldown(description.getCooldown());
      request.setNotificationUserGroupIds(
          description.getNotificationUserGroupIds().stream().toArray(String[]::new));

      ModifyScalingPolicyResponse response = client.ModifyScalingPolicy(request);
      return response;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<ScalingPolicy> getScalingPolicies(String asgId) {
    return iterQuery(
        (offset, limit) -> {
          DescribeScalingPoliciesRequest request = new DescribeScalingPoliciesRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          if (!StringUtils.isEmpty(asgId)) {
            Filter filter = new Filter();
            filter.setName("auto-scaling-group-id");
            filter.setValues(new String[] {asgId});
            request.setFilters(new Filter[] {filter});
          }

          DescribeScalingPoliciesResponse response = client.DescribeScalingPolicies(request);
          return Arrays.asList(response.getScalingPolicySet());
        });
  }

  public List<ScalingPolicy> getScalingPolicies() {
    return getScalingPolicies(null);
  }

  public DeleteScalingPolicyResponse deleteScalingPolicy(final String aspId) {
    try {
      DeleteScalingPolicyRequest request = new DeleteScalingPolicyRequest();
      request.setAutoScalingPolicyId(aspId);

      DeleteScalingPolicyResponse response = client.DeleteScalingPolicy(request);
      return response;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public String createScheduledAction(
      final String asgId, final UpsertTencentScheduledActionDescription description) {
    try {
      CreateScheduledActionRequest request = new CreateScheduledActionRequest();
      request.setAutoScalingGroupId(asgId);
      request.setScheduledActionName(
          description.getServerGroupName() + "-asst-" + new Date().getTime());
      request.setMaxSize(description.getMaxSize());
      request.setMinSize(description.getMinSize());
      request.setDesiredCapacity(description.getDesiredCapacity());
      request.setStartTime(description.getStartTime());
      request.setEndTime(description.getEndTime());
      request.setRecurrence(description.getRecurrence());

      CreateScheduledActionResponse response = client.CreateScheduledAction(request);
      return response.getScheduledActionId();
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public ModifyScheduledActionResponse modifyScheduledAction(
      final String asstId, final UpsertTencentScheduledActionDescription description) {
    try {
      ModifyScheduledActionRequest request = new ModifyScheduledActionRequest();
      request.setScheduledActionId(asstId);
      request.setMaxSize(description.getMaxSize());
      request.setMinSize(description.getMinSize());
      request.setDesiredCapacity(description.getDesiredCapacity());
      request.setStartTime(description.getStartTime());
      request.setEndTime(description.getEndTime());
      request.setRecurrence(description.getRecurrence());

      ModifyScheduledActionResponse response = client.ModifyScheduledAction(request);
      return response;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<ScheduledAction> getScheduledAction(String asgId) {
    return iterQuery(
        (offset, limit) -> {
          DescribeScheduledActionsRequest request = new DescribeScheduledActionsRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          if (!StringUtils.isEmpty(asgId)) {
            Filter filter = new Filter();
            filter.setName("auto-scaling-group-id");
            filter.setValues(new String[] {asgId});
            request.setFilters(new Filter[] {filter});
          }
          DescribeScheduledActionsResponse response = client.DescribeScheduledActions(request);
          return Arrays.asList(response.getScheduledActionSet());
        });
  }

  public List<ScheduledAction> getScheduledAction() {
    return getScheduledAction(null);
  }

  public DeleteScheduledActionResponse deleteScheduledAction(final String asstId) {
    try {
      DeleteScheduledActionRequest request = new DeleteScheduledActionRequest();
      request.setScheduledActionId(asstId);

      DeleteScheduledActionResponse response = client.DeleteScheduledAction(request);
      return response;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<AutoScalingNotification> getNotification() {
    return getNotification(null);
  }

  public List<AutoScalingNotification> getNotification(String asgId) {
    return iterQuery(
        (offset, limit) -> {
          DescribeNotificationConfigurationsRequest request =
              new DescribeNotificationConfigurationsRequest();
          request.setOffset(offset);
          request.setLimit(limit);

          if (!StringUtils.isEmpty(asgId)) {
            Filter filter = new Filter();
            filter.setName("auto-scaling-group-id");
            filter.setValues(new String[] {asgId});
            request.setFilters(new Filter[] {filter});
          }
          DescribeNotificationConfigurationsResponse response =
              client.DescribeNotificationConfigurations(request);
          return Arrays.asList(response.getAutoScalingNotificationSet());
        });
  }

  public CreateNotificationConfigurationResponse createNotification(
      String asgId, AutoScalingNotification notification) {
    try {
      CreateNotificationConfigurationRequest request = new CreateNotificationConfigurationRequest();
      request.setAutoScalingGroupId(asgId);
      request.setNotificationUserGroupIds(notification.getNotificationUserGroupIds());
      ;
      request.setNotificationTypes(notification.getNotificationTypes());
      return client.CreateNotificationConfiguration(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public final String getEndPoint() {
    return endPoint;
  }

  public static String getDefaultServerGroupTagKey() {
    return defaultServerGroupTagKey;
  }

  public static void setDefaultServerGroupTagKey(String defaultServerGroupTagKey) {
    AutoScalingClient.defaultServerGroupTagKey = defaultServerGroupTagKey;
  }

  private final String endPoint = "as.tencentcloudapi.com";
  private static String defaultServerGroupTagKey = "spinnaker:server-group-name";
  private AsClient client;
  private ClbClient clbClient;
  private int DEFAULT_LOAD_BALANCER_SERVICE_RETRY_TIME = 1000;
}
