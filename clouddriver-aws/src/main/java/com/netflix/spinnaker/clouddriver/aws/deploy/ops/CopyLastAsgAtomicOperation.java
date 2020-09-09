/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.EnabledMetric;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.netflix.frigga.Names;
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties;
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator;
import com.netflix.spinnaker.clouddriver.aws.model.SubnetData;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider;
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.*;
import java.util.stream.Collectors;
import jdk.internal.joptsimple.internal.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CopyLastAsgAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final Logger log = LoggerFactory.getLogger(CopyLastAsgAtomicOperation.class);
  private static final String BASE_PHASE = "COPY_LAST_ASG";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired private BasicAmazonDeployHandler basicAmazonDeployHandler;

  @Autowired private BasicAmazonDeployDescriptionValidator basicAmazonDeployDescriptionValidator;

  @Autowired private AmazonClientProvider amazonClientProvider;

  @Autowired private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired private RegionScopedProviderFactory regionScopedProviderFactory;

  @Autowired private LocalFileUserDataProperties localFileUserDataProperties;

  private final BasicAmazonDeployDescription description;

  public CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Copy Last ASG Operation...");

    final BasicAmazonDeployDescription.Source source = description.getSource();
    Set<String> targetRegions =
        Optional.ofNullable(description.getAvailabilityZones()).orElseGet(HashMap::new).keySet();
    if (targetRegions.isEmpty()) {
      targetRegions = new HashSet<>(Collections.singletonList(source.getRegion()));
    }

    if (hasAccountRegionAndAsg(source.getAccount(), source.getRegion(), source.getAsgName())) {
      Names sourceName = Names.parseName(source.getAsgName());
      description.setApplication(
          Optional.ofNullable(description.getApplication()).orElseGet(sourceName::getApp));

      description.setStack(
          Optional.ofNullable(description.getStack()).orElseGet(sourceName::getStack));

      description.setFreeFormDetails(
          Optional.ofNullable(description.getFreeFormDetails()).orElseGet(sourceName::getDetail));
    }

    DeploymentResult result = new DeploymentResult();

    String cluster =
        new AutoScalingGroupNameBuilder()
            .withAppName(description.getApplication())
            .withStack(description.getStack())
            .withDetail(description.getFreeFormDetails())
            .buildGroupName();

    List<BasicAmazonDeployDescription> deployDescriptions =
        targetRegions.stream()
            .map(
                targetRegion -> {
                  AutoScalingGroup ancestorAsg = null;
                  String sourceRegion;
                  NetflixAmazonCredentials sourceAsgCredentials;
                  if (hasAccountRegionAndAsg(
                      source.getAccount(), source.getRegion(), source.getAsgName())) {
                    sourceRegion = source.getRegion();
                    sourceAsgCredentials =
                        (NetflixAmazonCredentials)
                            accountCredentialsProvider.getCredentials(source.getAccount());
                    AmazonAutoScaling sourceAutoScaling =
                        amazonClientProvider.getAutoScaling(
                            sourceAsgCredentials, sourceRegion, true);
                    DescribeAutoScalingGroupsRequest request =
                        new DescribeAutoScalingGroupsRequest()
                            .withAutoScalingGroupNames(source.getAsgName());
                    List<AutoScalingGroup> ancestorAsgs =
                        sourceAutoScaling.describeAutoScalingGroups(request).getAutoScalingGroups();
                    ancestorAsg = ancestorAsgs.get(0);
                  } else {
                    sourceRegion = targetRegion;
                    sourceAsgCredentials = description.getCredentials();
                  }

                  boolean sourceIsTarget =
                      sourceRegion.equals(targetRegion)
                          && sourceAsgCredentials
                              .getName()
                              .equals(description.getCredentials().getName());
                  RegionScopedProvider sourceRegionScopedProvider =
                      regionScopedProviderFactory.forRegion(sourceAsgCredentials, sourceRegion);

                  BasicAmazonDeployDescription newDescription;
                  try {
                    newDescription = description.clone();
                  } catch (CloneNotSupportedException e) {
                    throw new IntegrationException(e);
                  }

                  if (ancestorAsg == null) {
                    getTask()
                        .updateStatus(
                            BASE_PHASE,
                            "Looking up last ASG in " + sourceRegion + " for " + cluster + "");
                    String latestServerGroupName =
                        sourceRegionScopedProvider
                            .getAWSServerGroupNameResolver()
                            .resolveLatestServerGroupName(cluster);
                    if (latestServerGroupName != null) {
                      ancestorAsg =
                          sourceRegionScopedProvider
                              .getAsgService()
                              .getAutoScalingGroup(latestServerGroupName);
                    }

                    BasicAmazonDeployDescription.Source newSource =
                        new BasicAmazonDeployDescription.Source();
                    newSource.setAccount(sourceAsgCredentials.getName());
                    newSource.setRegion(sourceRegionScopedProvider.getRegion());
                    newSource.setAsgName(ancestorAsg.getAutoScalingGroupName());

                    if (ancestorAsg != null) {
                      newDescription.setSource(newSource);

                      getTask()
                          .updateStatus(
                              BASE_PHASE,
                              "Using "
                                  + sourceRegion
                                  + "/"
                                  + ancestorAsg.getAutoScalingGroupName()
                                  + " as source.");
                    }
                  }

                  if (ancestorAsg != null) {
                    String imageId;
                    String instanceType;
                    String keyName;
                    String kernelId;
                    String ramdiskId;
                    String userData;
                    String classicLinkVPCId = null;
                    String spotPrice = null;
                    Boolean ebsOptimized;
                    String iamInstanceProfile = null;
                    Boolean instanceMonitoring = null;
                    Boolean associatePublicIpAddress = null;

                    List<String> securityGroups;
                    List<String> classicLinkVPCSecurityGroups = null;
                    if (ancestorAsg.getLaunchTemplate() != null) {
                      LaunchTemplateSpecification launchTemplate = ancestorAsg.getLaunchTemplate();
                      LaunchTemplateVersion launchTemplateVersion =
                          sourceRegionScopedProvider
                              .getLaunchTemplateService()
                              .getLaunchTemplateVersion(ancestorAsg.getLaunchTemplate())
                              .orElseThrow(
                                  () ->
                                      new IllegalStateException(
                                          "Requested launch template "
                                              + launchTemplate
                                              + " was not found"));

                      ResponseLaunchTemplateData launchTemplateData =
                          launchTemplateVersion.getLaunchTemplateData();
                      newDescription.setSetLaunchTemplate(true);
                      imageId = launchTemplateData.getImageId();
                      keyName = launchTemplateData.getKeyName();
                      kernelId = launchTemplateData.getKernelId();
                      userData = launchTemplateData.getUserData();
                      ramdiskId = launchTemplateData.getRamDiskId();
                      instanceType = launchTemplateData.getInstanceType();
                      securityGroups = launchTemplateData.getSecurityGroups();
                      ebsOptimized = launchTemplateData.getEbsOptimized();
                      if (launchTemplateData.getIamInstanceProfile() != null) {
                        iamInstanceProfile = launchTemplateData.getIamInstanceProfile().getName();
                      }

                      if (launchTemplateData.getMonitoring() != null) {
                        instanceMonitoring = launchTemplateData.getMonitoring().getEnabled();
                      }

                      if (launchTemplateData.getInstanceMarketOptions() != null) {
                        LaunchTemplateSpotMarketOptions options =
                            launchTemplateData.getInstanceMarketOptions().getSpotOptions();
                        if (options != null) {
                          spotPrice = options.getMaxPrice();
                        }
                      }

                      if (launchTemplateData.getMetadataOptions() != null) {
                        newDescription.setRequireIMDSv2(
                            "required"
                                .equals(launchTemplateData.getMetadataOptions().getHttpTokens()));
                      }

                      List<LaunchTemplateInstanceNetworkInterfaceSpecification> networkInterfaces =
                          Optional.ofNullable(launchTemplateData.getNetworkInterfaces())
                              .orElseGet(Collections::emptyList);
                      if (!networkInterfaces.isEmpty()
                          && networkInterfaces.stream()
                              .anyMatch(
                                  i ->
                                      i.getAssociatePublicIpAddress() != null
                                          && i.getAssociatePublicIpAddress())) {
                        associatePublicIpAddress = true;
                      }
                    } else {
                      LaunchConfiguration ancestorLaunchConfiguration =
                          sourceRegionScopedProvider
                              .getAsgService()
                              .getLaunchConfiguration(ancestorAsg.getLaunchConfigurationName());

                      keyName = ancestorLaunchConfiguration.getKeyName();
                      imageId = ancestorLaunchConfiguration.getImageId();
                      kernelId = ancestorLaunchConfiguration.getKernelId();
                      userData = ancestorLaunchConfiguration.getUserData();
                      ramdiskId = ancestorLaunchConfiguration.getRamdiskId();
                      spotPrice = ancestorLaunchConfiguration.getSpotPrice();
                      ebsOptimized = ancestorLaunchConfiguration.getEbsOptimized();
                      instanceType = ancestorLaunchConfiguration.getInstanceType();
                      securityGroups = ancestorLaunchConfiguration.getSecurityGroups();
                      classicLinkVPCId = ancestorLaunchConfiguration.getClassicLinkVPCId();
                      iamInstanceProfile = ancestorLaunchConfiguration.getIamInstanceProfile();
                      if (ancestorLaunchConfiguration.getInstanceMonitoring() != null) {
                        instanceMonitoring =
                            ancestorLaunchConfiguration.getInstanceMonitoring().getEnabled();
                      }

                      associatePublicIpAddress =
                          ancestorLaunchConfiguration.getAssociatePublicIpAddress();
                      classicLinkVPCSecurityGroups =
                          ancestorLaunchConfiguration.getClassicLinkVPCSecurityGroups();
                    }

                    if (ancestorAsg.getVPCZoneIdentifier() != null) {
                      getTask().updateStatus(BASE_PHASE, "Looking up subnet type...");

                      String vpcIdentifiers = ancestorAsg.getVPCZoneIdentifier().split(",")[0];
                      newDescription.setSubnetType(
                          Optional.ofNullable(description.getSubnetType())
                              .orElseGet(() -> getPurposeForSubnet(sourceRegion, vpcIdentifiers)));

                      getTask()
                          .updateStatus(
                              BASE_PHASE, "Found: " + newDescription.getSubnetType() + ".");
                    }

                    newDescription.setIamRole(
                        Optional.ofNullable(description.getIamRole()).orElse(iamInstanceProfile));
                    newDescription.setAmiName(
                        Optional.ofNullable(description.getAmiName()).orElse(imageId));

                    newDescription.setAvailabilityZones(
                        Collections.singletonMap(
                            targetRegion,
                            Optional.ofNullable(
                                    description.getAvailabilityZones().get(targetRegion))
                                .orElseGet(ancestorAsg::getAvailabilityZones)));

                    newDescription.setInstanceType(
                        Optional.ofNullable(description.getInstanceType()).orElse(instanceType));
                    newDescription.setLoadBalancers(
                        Optional.ofNullable(description.getLoadBalancers())
                            .orElseGet(ancestorAsg::getLoadBalancerNames));
                    newDescription.setTargetGroups(description.getTargetGroups());
                    if (newDescription.getTargetGroups() == null
                        && ancestorAsg.getTargetGroupARNs() != null
                        && !ancestorAsg.getTargetGroupARNs().isEmpty()) {
                      List<TargetGroup> targetGroups =
                          sourceRegionScopedProvider
                              .getAmazonElasticLoadBalancingV2(true)
                              .describeTargetGroups(
                                  new DescribeTargetGroupsRequest()
                                      .withTargetGroupArns(ancestorAsg.getTargetGroupARNs()))
                              .getTargetGroups();
                      newDescription.setTargetGroups(
                          targetGroups.stream()
                              .map(i -> i.getTargetGroupName())
                              .collect(Collectors.toList()));
                    }

                    newDescription.setSecurityGroups(
                        Optional.ofNullable(description.getSecurityGroups())
                            .orElseGet(
                                () ->
                                    translateSecurityGroupIds(
                                        securityGroups,
                                        sourceRegionScopedProvider.getSecurityGroupService(),
                                        sourceIsTarget)));

                    Integer min = null;
                    Integer max = null;
                    Integer desired = null;
                    if (description.getCapacity() != null) {
                      min = description.getCapacity().getMin();
                      max = description.getCapacity().getMax();
                      desired = description.getCapacity().getDesired();
                    }

                    newDescription
                        .getCapacity()
                        .setMin(Optional.ofNullable(min).orElseGet(ancestorAsg::getMinSize));

                    newDescription
                        .getCapacity()
                        .setMax(Optional.ofNullable(max).orElseGet(ancestorAsg::getMaxSize));

                    newDescription
                        .getCapacity()
                        .setDesired(
                            Optional.ofNullable(desired)
                                .orElseGet(ancestorAsg::getDesiredCapacity));

                    newDescription.setKeyPair(
                        Optional.ofNullable(description.getKeyPair())
                            .orElseGet(
                                () ->
                                    sourceIsTarget
                                        ? keyName
                                        : description.getCredentials().getDefaultKeyPair()));

                    newDescription.setAssociatePublicIpAddress(
                        Optional.ofNullable(description.getAssociatePublicIpAddress())
                            .orElse(associatePublicIpAddress));

                    newDescription.setCooldown(
                        Optional.ofNullable(description.getCooldown())
                            .orElseGet(ancestorAsg::getDefaultCooldown));

                    List<EnabledMetric> enabledMetrics = ancestorAsg.getEnabledMetrics();
                    newDescription.setEnabledMetrics(
                        Optional.ofNullable(description.getEnabledMetrics())
                            .orElseGet(
                                () ->
                                    enabledMetrics.stream()
                                        .map(EnabledMetric::getMetric)
                                        .collect(Collectors.toList())));

                    newDescription.setHealthCheckGracePeriod(
                        Optional.ofNullable(description.getHealthCheckGracePeriod())
                            .orElseGet(ancestorAsg::getHealthCheckGracePeriod));

                    newDescription.setHealthCheckType(
                        Optional.ofNullable(description.getHealthCheckType())
                            .orElseGet(ancestorAsg::getHealthCheckType));

                    List<SuspendedProcess> suspendedProcesses = ancestorAsg.getSuspendedProcesses();
                    newDescription.setSuspendedProcesses(
                        Optional.ofNullable(description.getSuspendedProcesses())
                            .orElseGet(
                                () ->
                                    suspendedProcesses.stream()
                                        .map(SuspendedProcess::getProcessName)
                                        .collect(Collectors.toSet())));

                    newDescription.setTerminationPolicies(
                        Optional.ofNullable(description.getTerminationPolicies())
                            .orElseGet(ancestorAsg::getTerminationPolicies));

                    newDescription.setKernelId(
                        Optional.ofNullable(description.getKernelId())
                            .orElse(Optional.ofNullable(kernelId).orElse(null)));

                    newDescription.setRamdiskId(
                        Optional.ofNullable(description.getRamdiskId())
                            .orElse(Optional.ofNullable(ramdiskId).orElse(null)));

                    newDescription.setInstanceMonitoring(
                        Optional.ofNullable(description.getInstanceMonitoring())
                            .orElse(instanceMonitoring));
                    newDescription.setEbsOptimized(
                        Optional.ofNullable(description.getEbsOptimized()).orElse(ebsOptimized));

                    newDescription.setClassicLinkVpcId(
                        Optional.ofNullable(description.getClassicLinkVpcId())
                            .orElse(classicLinkVPCId));

                    final List<String> classicLinkVPCSecurityGroupsCopy =
                        classicLinkVPCSecurityGroups;
                    newDescription.setClassicLinkVpcSecurityGroups(
                        Optional.ofNullable(description.getClassicLinkVpcSecurityGroups())
                            .orElseGet(
                                () ->
                                    translateSecurityGroupIds(
                                        classicLinkVPCSecurityGroupsCopy,
                                        sourceRegionScopedProvider.getSecurityGroupService(),
                                        sourceIsTarget)));

                    Map<String, String> tags =
                        ancestorAsg.getTags().stream()
                            .collect(Collectors.toMap(i -> i.getKey(), i -> i.getValue()));

                    newDescription.setTags(tags);

                    /*
                     Copy over the ancestor user data only if the UserDataProviders behavior is disabled and no user data is provided
                     on this request.
                     This is to avoid having duplicate user data.
                    */
                    if (localFileUserDataProperties != null
                        && !localFileUserDataProperties.getEnabled()) {
                      newDescription.setBase64UserData(
                          Optional.ofNullable(description.getBase64UserData()).orElse(userData));
                    }

                    if (description.getSpotPrice() == null) {
                      newDescription.setSpotPrice(spotPrice);
                    } else if (description.getSpotPrice() != null) {
                      newDescription.setSpotPrice(description.getSpotPrice());
                    } else { // ""
                      newDescription.setSpotPrice(null);
                    }
                  }

                  getTask()
                      .updateStatus(
                          BASE_PHASE, "Validating clone configuration for $targetRegion.");
                  DescriptionValidationErrors errors =
                      new DescriptionValidationErrors(newDescription);
                  basicAmazonDeployDescriptionValidator.validate(
                      priorOutputs, newDescription, errors);
                  if (errors.hasErrors()) {
                    throw new DescriptionValidationException(errors);
                  }

                  return newDescription;
                })
            .collect(Collectors.toList());

    for (BasicAmazonDeployDescription newDescription : deployDescriptions) {
      String targetRegion =
          newDescription.getAvailabilityZones().keySet().stream().findFirst().orElse(null);
      getTask().updateStatus(BASE_PHASE, "Initiating deployment in $targetRegion.");
      DeploymentResult thisResult = basicAmazonDeployHandler.handle(newDescription, priorOutputs);

      result.getServerGroupNames().addAll(thisResult.getServerGroupNames());
      result.getDeployedNames().addAll(thisResult.getDeployedNames());
      result.getDeployments().addAll(thisResult.getDeployments());
      result.getCreatedArtifacts().addAll(thisResult.getCreatedArtifacts());
      result.getMessages().addAll(thisResult.getMessages());
      thisResult
          .getServerGroupNameByRegion()
          .entrySet()
          .forEach(i -> result.getServerGroupNameByRegion().put(i.getKey(), i.getValue()));

      thisResult
          .getDeployedNamesByLocation()
          .entrySet()
          .forEach(i -> result.getDeployedNamesByLocation().put(i.getKey(), i.getValue()));

      getTask()
          .updateStatus(
              BASE_PHASE,
              "Deployment complete in "
                  + targetRegion
                  + ". New ASGs = "
                  + result.getServerGroupNames()
                  + "");
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Finished copying last ASG for "
                + cluster
                + ". New ASGs = "
                + result.getServerGroupNames()
                + "");

    return result;
  }

  String getPurposeForSubnet(String region, String subnetId) {
    AmazonEC2 amazonEC2 =
        amazonClientProvider.getAmazonEC2(description.getCredentials(), region, true);
    DescribeSubnetsResult result =
        amazonEC2.describeSubnets(new DescribeSubnetsRequest().withSubnetIds(subnetId));
    if (result != null && result.getSubnets() != null && !result.getSubnets().isEmpty()) {
      SubnetData data = SubnetData.from(result.getSubnets().get(0));
      return data.getPurpose();
    }
    return null;
  }

  private List<String> translateSecurityGroupIds(
      List<String> securityGroupIds,
      SecurityGroupService securityGroupService,
      boolean sourceIsTarget) {
    if (!sourceIsTarget) {
      return securityGroupIds;
    }

    if (securityGroupIds != null) {
      return new ArrayList<>(
          securityGroupService.getSecurityGroupNamesFromIds(securityGroupIds).keySet());
    }

    return Collections.emptyList();
  }

  private static boolean hasAccountRegionAndAsg(String account, String region, String asgName) {
    return Strings.isNullOrEmpty(region)
        && !Strings.isNullOrEmpty(account)
        && !Strings.isNullOrEmpty(asgName);
  }
}
