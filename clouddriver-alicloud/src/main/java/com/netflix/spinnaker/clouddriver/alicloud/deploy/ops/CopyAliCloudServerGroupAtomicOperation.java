/*
 * Copyright 2022 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationRequest;
import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationRequest.SpotPriceLimit;
import com.aliyuncs.ess.model.v20140828.CreateScalingConfigurationResponse;
import com.aliyuncs.ess.model.v20140828.CreateScalingGroupRequest;
import com.aliyuncs.ess.model.v20140828.CreateScalingGroupResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration.DataDisk;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration.SpotPriceModel;
import com.aliyuncs.ess.model.v20140828.DescribeScalingConfigurationsResponse.ScalingConfiguration.Tag;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsRequest;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup.VServerGroup;
import com.aliyuncs.ess.model.v20140828.DescribeScalingGroupsResponse.ScalingGroup.VServerGroup.VServerGroupAttribute;
import com.aliyuncs.ess.model.v20140828.EnableScalingGroupRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.AliCloudServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.BasicAliCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CopyAliCloudServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {

  private final List<ClusterProvider> clusterProviders;

  private final ObjectMapper objectMapper;

  private final BasicAliCloudDeployDescription description;

  private final ClientFactory clientFactory;

  static final Gson gson = new Gson();

  public CopyAliCloudServerGroupAtomicOperation(
      ObjectMapper objectMapper,
      BasicAliCloudDeployDescription description,
      ClientFactory clientFactory,
      List<ClusterProvider> clusterProviders) {
    this.objectMapper = objectMapper;
    this.description = description;
    this.clientFactory = clientFactory;
    this.clusterProviders = clusterProviders;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    DeploymentResult result = new DeploymentResult();
    // create scaling group
    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(), description.getCredentials().getCredentialsProvider());
    AliCloudServerGroupNameResolver resolver =
        new AliCloudServerGroupNameResolver(
            description.getCredentials().getName(), description.getRegion(), clusterProviders);
    String serverGroupName =
        resolver.resolveNextServerGroupName(
            description.getApplication(),
            description.getStack(),
            description.getFreeFormDetails(),
            false);
    description.setScalingGroupName(serverGroupName);
    String asgName = description.getSource().getAsgName();
    DescribeScalingGroupsRequest request = new DescribeScalingGroupsRequest();
    request.setScalingGroupName(asgName);
    DescribeScalingGroupsResponse response;
    try {
      response = client.getAcsResponse(request);
      if (response.getScalingGroups().size() == 0) {
        throw new AliCloudException("Old server group is does not exist");
      }
      ScalingGroup scalingGroup = response.getScalingGroups().get(0);

      CreateScalingGroupRequest createScalingGroupRequest =
          buildScalingGroupData(description, scalingGroup);

      CreateScalingGroupResponse createScalingGroupResponse =
          client.getAcsResponse(createScalingGroupRequest);
      String scalingGroupId = createScalingGroupResponse.getScalingGroupId();
      description.setScalingGroupId(scalingGroupId);
      DescribeScalingConfigurationsRequest scalingConfigurationsRequest =
          new DescribeScalingConfigurationsRequest();
      scalingConfigurationsRequest.setScalingGroupId(scalingGroup.getScalingGroupId());
      scalingConfigurationsRequest.setScalingConfigurationId1(
          scalingGroup.getActiveScalingConfigurationId());
      DescribeScalingConfigurationsResponse scalingConfiguration =
          client.getAcsResponse(scalingConfigurationsRequest);

      CreateScalingConfigurationRequest createScalingConfigurationRequest =
          buildScalingConfigurationData(
              description.getScalingConfigurations(), scalingConfiguration);

      createScalingConfigurationRequest.setScalingGroupId(description.getScalingGroupId());
      CreateScalingConfigurationResponse configurationResponse =
          client.getAcsResponse(createScalingConfigurationRequest);

      EnableScalingGroupRequest enableScalingGroupRequest = new EnableScalingGroupRequest();
      enableScalingGroupRequest.setScalingGroupId(description.getScalingGroupId());
      enableScalingGroupRequest.setActiveScalingConfigurationId(
          configurationResponse.getScalingConfigurationId());
      client.getAcsResponse(enableScalingGroupRequest);

    } catch (ServerException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    buildResult(description, result);
    return result;
  }

  private CreateScalingGroupRequest buildScalingGroupData(
      BasicAliCloudDeployDescription description, ScalingGroup scalingGroup) {
    CreateScalingGroupRequest request = new CreateScalingGroupRequest();
    request.setMaxSize(
        description.getMaxSize() != null
            ? description.getMaxSize()
            : (description.getCapacity().getMax() != null
                ? description.getCapacity().getMax()
                : scalingGroup.getMaxSize()));
    request.setMinSize(
        description.getMinSize() != null
            ? description.getMinSize()
            : (description.getCapacity().getMin() != null
                ? description.getCapacity().getMin()
                : scalingGroup.getMinSize()));
    request.setClientToken(description.getClientToken());
    if (description.getDBInstanceIds() != null) {
      request.setDBInstanceIds(description.getDBInstanceIds());
    } else {
      if (scalingGroup.getDBInstanceIds().size() > 0) {
        request.setDBInstanceIds(gson.toJson(scalingGroup.getDBInstanceIds()));
      }
    }
    request.setDefaultCooldown(
        description.getDefaultCooldown() != null
            ? description.getDefaultCooldown()
            : scalingGroup.getDefaultCooldown());
    request.setHealthCheckType(
        description.getHealthCheckType() != null
            ? description.getHealthCheckType()
            : scalingGroup.getHealthCheckType());
    request.setLaunchTemplateId(
        description.getLaunchTemplateId() != null
            ? description.getLaunchTemplateId()
            : scalingGroup.getLaunchTemplateId());
    request.setLaunchTemplateVersion(
        description.getLaunchTemplateVersion() != null
            ? description.getLaunchTemplateVersion()
            : scalingGroup.getLaunchTemplateVersion());
    if (description.getLifecycleHooks() != null && description.getLifecycleHooks().size() > 0) {
      request.setLifecycleHooks(description.getLifecycleHooks());
    }
    if (StringUtils.isNotEmpty(description.getLoadBalancerIds())) {
      request.setLoadBalancerIds(description.getLoadBalancerIds());
    } else {
      if (scalingGroup.getLoadBalancerIds().size() > 0) {
        request.setLoadBalancerIds(gson.toJson(scalingGroup));
      }
    }
    request.setMultiAZPolicy(
        description.getMultiAZPolicy() != null
            ? description.getMultiAZPolicy()
            : scalingGroup.getMultiAZPolicy());
    if (StringUtils.isNotEmpty(description.getRemovalPolicy1())) {
      request.setRemovalPolicy1(description.getRemovalPolicy1());
    } else {
      if (scalingGroup.getRemovalPolicies().size() >= 1) {
        request.setRemovalPolicy1(scalingGroup.getRemovalPolicies().get(0));
      }
    }
    if (StringUtils.isNotEmpty(description.getRemovalPolicy2())) {
      request.setRemovalPolicy2(description.getRemovalPolicy2());
    } else {
      if (scalingGroup.getRemovalPolicies().size() == 2) {
        request.setRemovalPolicy2(scalingGroup.getRemovalPolicies().get(1));
      }
    }
    request.setScalingGroupName(
        description.getScalingGroupName() != null
            ? description.getScalingGroupName()
            : scalingGroup.getScalingGroupName());
    request.setScalingPolicy(
        description.getScalingPolicy() != null
            ? description.getScalingPolicy()
            : scalingGroup.getScalingPolicy());
    if (description.getVServerGroups() != null && description.getVServerGroups().size() > 0) {
      request.setVServerGroups(description.getVServerGroups());
    } else {
      if (scalingGroup.getVServerGroups().size() > 0) {
        List<CreateScalingGroupRequest.VServerGroup> serverGroups = new ArrayList<>();
        for (VServerGroup vServerGroup : scalingGroup.getVServerGroups()) {
          CreateScalingGroupRequest.VServerGroup group =
              new CreateScalingGroupRequest.VServerGroup();
          group.setLoadBalancerId(vServerGroup.getLoadBalancerId());
          List<CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute>
              vServerGroupAttributes = new ArrayList<>();
          for (VServerGroupAttribute vServerGroupAttribute :
              vServerGroup.getVServerGroupAttributes()) {
            CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute attribute =
                new CreateScalingGroupRequest.VServerGroup.VServerGroupAttribute();
            attribute.setPort(vServerGroupAttribute.getPort());
            attribute.setVServerGroupId(vServerGroupAttribute.getVServerGroupId());
            attribute.setWeight(vServerGroupAttribute.getWeight());
            vServerGroupAttributes.add(attribute);
          }
          group.setVServerGroupAttributes(vServerGroupAttributes);
          serverGroups.add(group);
        }
        request.setVServerGroups(serverGroups);
      }
    }
    request.setVSwitchId(
        description.getVSwitchId() != null
            ? description.getVSwitchId()
            : scalingGroup.getVSwitchId());
    request.setVSwitchIds(
        description.getVSwitchIds() != null
            ? description.getVSwitchIds()
            : scalingGroup.getVSwitchIds());

    return request;
  }

  private CreateScalingConfigurationRequest buildScalingConfigurationData(
      List<CreateScalingConfigurationRequest> scalingConfigurations,
      DescribeScalingConfigurationsResponse describeScalingConfigurationsResponse) {
    CreateScalingConfigurationRequest createScalingConfigurationRequest =
        new CreateScalingConfigurationRequest();
    CreateScalingConfigurationRequest scalingConfigurationNew = null;
    if (scalingConfigurations != null && scalingConfigurations.size() > 0) {
      scalingConfigurationNew = scalingConfigurations.get(0);
    } else {
      scalingConfigurationNew = new CreateScalingConfigurationRequest();
    }
    ScalingConfiguration scalingConfigurationOld = null;
    if (describeScalingConfigurationsResponse.getScalingConfigurations().size() > 0) {
      scalingConfigurationOld =
          describeScalingConfigurationsResponse.getScalingConfigurations().get(0);
    } else {
      scalingConfigurationOld = new ScalingConfiguration();
    }
    createScalingConfigurationRequest.setCpu(
        scalingConfigurationNew.getCpu() != null
            ? scalingConfigurationNew.getCpu()
            : scalingConfigurationOld.getCpu());
    if (scalingConfigurationNew.getDataDisks() != null
        && scalingConfigurationNew.getDataDisks().size() > 0) {
      createScalingConfigurationRequest.setDataDisks(scalingConfigurationNew.getDataDisks());
    } else {
      if (scalingConfigurationOld.getDataDisks().size() > 0) {
        List<CreateScalingConfigurationRequest.DataDisk> dataDisks = new ArrayList<>();
        for (DataDisk dataDisk : scalingConfigurationOld.getDataDisks()) {
          CreateScalingConfigurationRequest.DataDisk disk =
              new CreateScalingConfigurationRequest.DataDisk();
          disk.setCategory(dataDisk.getCategory());
          disk.setDeleteWithInstance(dataDisk.getDeleteWithInstance());
          disk.setDescription(dataDisk.getDescription());
          disk.setDevice(dataDisk.getDevice());
          disk.setDiskName(dataDisk.getDiskName());
          disk.setEncrypted(dataDisk.getEncrypted());
          disk.setKMSKeyId(dataDisk.getKMSKeyId());
          disk.setSize(dataDisk.getSize());
          disk.setSnapshotId(dataDisk.getSnapshotId());
          dataDisks.add(disk);
        }
        createScalingConfigurationRequest.setDataDisks(dataDisks);
      }
    }
    createScalingConfigurationRequest.setDeploymentSetId(
        StringUtils.isNotEmpty(scalingConfigurationNew.getDeploymentSetId())
            ? scalingConfigurationNew.getDeploymentSetId()
            : scalingConfigurationOld.getDeploymentSetId());
    createScalingConfigurationRequest.setHostName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getHostName())
            ? scalingConfigurationNew.getHostName()
            : scalingConfigurationOld.getHostName());
    createScalingConfigurationRequest.setImageId(
        StringUtils.isNotEmpty(scalingConfigurationNew.getImageId())
            ? scalingConfigurationNew.getImageId()
            : scalingConfigurationOld.getImageId());
    createScalingConfigurationRequest.setImageName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getImageName())
            ? scalingConfigurationNew.getImageName()
            : scalingConfigurationOld.getImageName());
    createScalingConfigurationRequest.setInstanceName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getInstanceName())
            ? scalingConfigurationNew.getInstanceName()
            : scalingConfigurationOld.getInstanceName());
    createScalingConfigurationRequest.setInstanceType(
        StringUtils.isNotEmpty(scalingConfigurationNew.getInstanceType())
            ? scalingConfigurationNew.getInstanceType()
            : scalingConfigurationOld.getInstanceType());
    if (scalingConfigurationNew.getInstanceTypes() != null
        && scalingConfigurationNew.getInstanceTypes().size() > 0) {
      createScalingConfigurationRequest.setInstanceTypes(
          scalingConfigurationNew.getInstanceTypes());
    } else {
      createScalingConfigurationRequest.setInstanceTypes(
          scalingConfigurationOld.getInstanceTypes());
    }
    createScalingConfigurationRequest.setInternetChargeType(
        StringUtils.isNotEmpty(scalingConfigurationNew.getInternetChargeType())
            ? scalingConfigurationNew.getInternetChargeType()
            : scalingConfigurationOld.getInternetChargeType());
    createScalingConfigurationRequest.setInternetMaxBandwidthIn(
        scalingConfigurationNew.getInternetMaxBandwidthIn() != null
            ? scalingConfigurationNew.getInternetMaxBandwidthIn()
            : scalingConfigurationOld.getInternetMaxBandwidthIn());
    createScalingConfigurationRequest.setInternetMaxBandwidthOut(
        scalingConfigurationNew.getInternetMaxBandwidthOut() != null
            ? scalingConfigurationNew.getInternetMaxBandwidthOut()
            : scalingConfigurationOld.getInternetMaxBandwidthOut());
    // createScalingConfigurationRequest.setIoOptimized(StringUtils.isNotEmpty(scalingConfigurationNew.getIoOptimized()) ? scalingConfigurationNew.getIoOptimized() : scalingConfigurationOld.getIoOptimized());
    createScalingConfigurationRequest.setKeyPairName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getKeyPairName())
            ? scalingConfigurationNew.getKeyPairName()
            : scalingConfigurationOld.getKeyPairName());
    createScalingConfigurationRequest.setLoadBalancerWeight(
        scalingConfigurationNew.getLoadBalancerWeight() != null
            ? scalingConfigurationNew.getLoadBalancerWeight()
            : scalingConfigurationOld.getLoadBalancerWeight());
    createScalingConfigurationRequest.setMemory(
        scalingConfigurationNew.getMemory() != null
            ? scalingConfigurationNew.getMemory()
            : scalingConfigurationOld.getMemory());
    createScalingConfigurationRequest.setPassword(scalingConfigurationNew.getPassword());
    createScalingConfigurationRequest.setPasswordInherit(
        scalingConfigurationNew.getPasswordInherit() != null
            ? scalingConfigurationNew.getPasswordInherit()
            : scalingConfigurationOld.getPasswordInherit());
    createScalingConfigurationRequest.setRamRoleName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getRamRoleName())
            ? scalingConfigurationNew.getRamRoleName()
            : scalingConfigurationOld.getRamRoleName());
    createScalingConfigurationRequest.setResourceGroupId(
        StringUtils.isNotEmpty(scalingConfigurationNew.getResourceGroupId())
            ? scalingConfigurationNew.getResourceGroupId()
            : scalingConfigurationNew.getResourceGroupId());
    createScalingConfigurationRequest.setScalingConfigurationName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getScalingConfigurationName())
            ? scalingConfigurationNew.getScalingConfigurationName()
            : scalingConfigurationOld.getScalingConfigurationName());
    createScalingConfigurationRequest.setSecurityEnhancementStrategy(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSecurityEnhancementStrategy())
            ? scalingConfigurationNew.getSecurityEnhancementStrategy()
            : scalingConfigurationOld.getSecurityEnhancementStrategy());
    createScalingConfigurationRequest.setSecurityGroupId(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSecurityGroupId())
            ? scalingConfigurationNew.getSecurityGroupId()
            : scalingConfigurationOld.getSecurityGroupId());
    if (scalingConfigurationNew.getSpotPriceLimits() != null
        && scalingConfigurationNew.getSpotPriceLimits().size() > 0) {
      createScalingConfigurationRequest.setSpotPriceLimits(
          scalingConfigurationNew.getSpotPriceLimits());
    } else {
      if (scalingConfigurationOld.getSpotPriceLimit().size() > 0) {
        List<SpotPriceLimit> spotPriceLimits = new ArrayList<>();
        for (SpotPriceModel spotPriceModel : scalingConfigurationOld.getSpotPriceLimit()) {
          SpotPriceLimit limit = new SpotPriceLimit();
          limit.setInstanceType(spotPriceModel.getInstanceType());
          limit.setPriceLimit(spotPriceModel.getPriceLimit());
          spotPriceLimits.add(limit);
        }
        createScalingConfigurationRequest.setSpotPriceLimits(spotPriceLimits);
      }
    }
    createScalingConfigurationRequest.setSpotStrategy(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSpotStrategy())
            ? scalingConfigurationNew.getSpotStrategy()
            : scalingConfigurationOld.getSpotStrategy());
    createScalingConfigurationRequest.setSystemDiskCategory(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSystemDiskCategory())
            ? scalingConfigurationNew.getSystemDiskCategory()
            : scalingConfigurationOld.getSystemDiskCategory());
    createScalingConfigurationRequest.setSystemDiskDescription(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSystemDiskDescription())
            ? scalingConfigurationNew.getSystemDiskDescription()
            : scalingConfigurationOld.getSystemDiskDescription());
    createScalingConfigurationRequest.setSystemDiskDiskName(
        StringUtils.isNotEmpty(scalingConfigurationNew.getSystemDiskDiskName())
            ? scalingConfigurationNew.getSystemDiskDiskName()
            : scalingConfigurationOld.getSystemDiskName());
    createScalingConfigurationRequest.setSystemDiskSize(
        scalingConfigurationNew.getSystemDiskSize() != null
            ? scalingConfigurationNew.getSystemDiskSize()
            : scalingConfigurationOld.getSystemDiskSize());
    if (StringUtils.isNotEmpty(scalingConfigurationNew.getTags())) {
      createScalingConfigurationRequest.setTags(scalingConfigurationNew.getTags());
    } else {
      List<Tag> tags = scalingConfigurationOld.getTags();
      if (tags.size() > 0) {
        createScalingConfigurationRequest.setTags(gson.toJson(tags));
      }
    }
    createScalingConfigurationRequest.setUserData(
        StringUtils.isNotEmpty(scalingConfigurationNew.getUserData())
            ? scalingConfigurationNew.getUserData()
            : scalingConfigurationOld.getUserData());
    return createScalingConfigurationRequest;
  }

  private void buildResult(BasicAliCloudDeployDescription description, DeploymentResult result) {

    List<String> serverGroupNames = new ArrayList<>();
    serverGroupNames.add(description.getRegion() + ":" + description.getScalingGroupName());
    result.setServerGroupNames(serverGroupNames);

    Map<String, String> serverGroupNameByRegion = new HashMap<>();
    serverGroupNameByRegion.put(description.getRegion(), description.getScalingGroupName());
    result.setServerGroupNameByRegion(serverGroupNameByRegion);

    Set<Deployment> deployments = new HashSet<>();

    DeploymentResult.Deployment.Capacity capacity = new DeploymentResult.Deployment.Capacity();
    capacity.setMax(description.getMaxSize());
    capacity.setMin(description.getMinSize());
    capacity.setDesired(description.getMinSize());

    DeploymentResult.Deployment deployment = new DeploymentResult.Deployment();
    deployment.setCloudProvider(AliCloudProvider.ID);
    deployment.setAccount(description.getCredentials().getName());
    deployment.setCapacity(capacity);
    deployment.setLocation(description.getRegion());
    deployment.setServerGroupName(description.getScalingGroupName());

    deployments.add(deployment);
    result.setDeployments(deployments);
  }
}
