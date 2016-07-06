/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Capacity;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Source;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateServerGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ServerGroupMigrator.ServerGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;

import java.util.*;
import java.util.stream.Collectors;

public interface MigrateServerGroupStrategy {
  AmazonClientProvider getAmazonClientProvider();

  RegionScopedProviderFactory getRegionScopedProviderFactory();

  DeployDefaults getDeployDefaults();

  MigrateSecurityGroupStrategy getMigrateSecurityGroupStrategy();

  MigrateLoadBalancerStrategy getMigrateLoadBalancerStrategy();

  BasicAmazonDeployHandler getBasicAmazonDeployHandler();

  /**
   * Migrates a server group and its associated load balancers and security groups from one location to another
   *
   * @param source       the source server group
   * @param target       the target location in which to migrate
   * @param sourceLookup a security group lookup cache for the source region
   * @param targetLookup a security group lookup cache for the target region (may be the same object as the sourceLookup)
   * @param subnetType   the subnetType in which to migrate the server group (should be null for EC Classic migrations)
   * @param iamRole      the iamRole to use when migrating (optional)
   * @param keyPair      the keyPair to use when migrating (optional)
   * @param targetAmi    the target imageId to use when migrating (optional)
   * @param dryRun       whether to perform the migration or simply calculate the migration
   * @return a result set indicating the components required to perform the migration (if a dry run), or the objects
   * updated by the migration (if not a dry run)
   */
  default MigrateServerGroupResult generateResults(ServerGroupLocation source, ServerGroupLocation target,
                                                   SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                                   String subnetType, String iamRole, String keyPair, String targetAmi,
                                                   boolean dryRun) {
    AsgService asgService = getRegionScopedProviderFactory().forRegion(source.getCredentials(), source.getRegion())
      .getAsgService();

    AutoScalingGroup sourceGroup = asgService.getAutoScalingGroup(source.getName());

    if (sourceGroup == null) {
      throw new IllegalStateException("Error retrieving source server group: " + source.getName());
    }

    LaunchConfiguration sourceLaunchConfig = asgService.getLaunchConfiguration(sourceGroup.getLaunchConfigurationName());

    if (sourceLaunchConfig == null) {
      throw new IllegalStateException("Could not find launch config: " + sourceGroup.getLaunchConfigurationName());
    }

    Names names = Names.parseName(source.getName());

    List<MigrateLoadBalancerResult> targetLoadBalancers = sourceGroup.getLoadBalancerNames().stream().map(lbName ->
      getMigrateLoadBalancerResult(source, target, sourceLookup, targetLookup, subnetType, dryRun, lbName)
    ).collect(Collectors.toList());

    List<String> securityGroupNames = sourceLaunchConfig.getSecurityGroups().stream()
      .map(sg -> sourceLookup.getSecurityGroupById(source.getCredentialAccount(), sg, source.getVpcId()).getSecurityGroup())
      .map(SecurityGroup::getGroupName).collect(Collectors.toList());

    List<MigrateSecurityGroupResult> targetSecurityGroups = securityGroupNames.stream().map(group ->
      getMigrateSecurityGroupResult(source, target, sourceLookup, targetLookup, dryRun, group)
    ).collect(Collectors.toList());

    if (getDeployDefaults().getAddAppGroupToServerGroup()) {
      targetSecurityGroups.add(generateAppSecurityGroup(source, target, sourceLookup, targetLookup, dryRun));
    }

    Map<String, List<String>> zones = new HashMap<>();
    zones.put(target.getRegion(), target.getAvailabilityZones());


    DeploymentResult result;
    if (!dryRun) {
      Capacity capacity = getCapacity();
      BasicAmazonDeployDescription deployDescription = new BasicAmazonDeployDescription();
      deployDescription.setSource(getSource(source));
      deployDescription.setCredentials(target.getCredentials());
      deployDescription.setAmiName(targetAmi != null ? targetAmi : sourceLaunchConfig.getImageId());
      deployDescription.setApplication(names.getApp());
      deployDescription.setStack(names.getStack());
      deployDescription.setFreeFormDetails(names.getDetail());
      deployDescription.setInstanceMonitoring(sourceLaunchConfig.getInstanceMonitoring().getEnabled());
      deployDescription.setInstanceType(sourceLaunchConfig.getInstanceType());
      deployDescription.setIamRole(iamRole != null ? iamRole : sourceLaunchConfig.getIamInstanceProfile());
      deployDescription.setKeyPair(keyPair != null ? keyPair : sourceLaunchConfig.getKeyName());
      deployDescription.setAssociatePublicIpAddress(sourceLaunchConfig.getAssociatePublicIpAddress());
      deployDescription.setCooldown(sourceGroup.getDefaultCooldown());
      deployDescription.setHealthCheckGracePeriod(sourceGroup.getHealthCheckGracePeriod());
      deployDescription.setHealthCheckType(sourceGroup.getHealthCheckType());
      deployDescription.setSuspendedProcesses(sourceGroup.getSuspendedProcesses().stream().map(SuspendedProcess::getProcessName).collect(Collectors.toSet()));
      deployDescription.setTerminationPolicies(sourceGroup.getTerminationPolicies());
      deployDescription.setKernelId(sourceLaunchConfig.getKernelId());
      deployDescription.setEbsOptimized(sourceLaunchConfig.getEbsOptimized());
      deployDescription.setBase64UserData(sourceLaunchConfig.getUserData());
      deployDescription.setLoadBalancers(targetLoadBalancers.stream().map(MigrateLoadBalancerResult::getTargetName).collect(Collectors.toList()));
      deployDescription.setSecurityGroups(targetSecurityGroups.stream().map(sg -> sg.getTarget().getTargetName()).collect(Collectors.toList()));
      deployDescription.setAvailabilityZones(zones);
      deployDescription.setStartDisabled(true);
      deployDescription.setCapacity(capacity);
      deployDescription.setSubnetType(subnetType);

      BasicAmazonDeployDescription description = generateDescription(source, deployDescription);

      result = getBasicAmazonDeployHandler().handle(description, new ArrayList());
    } else {
      result = new DeploymentResult();
      String targetName = getRegionScopedProviderFactory().forRegion(target.getCredentials(), target.getRegion())
        .getAWSServerGroupNameResolver()
        .resolveNextServerGroupName(names.getApp(), names.getStack(), names.getDetail(), false);

      result.setServerGroupNames(Collections.singletonList(targetName));
    }
    MigrateServerGroupResult migrateResult = new MigrateServerGroupResult();
    migrateResult.setServerGroupName(result.getServerGroupNames().get(0));
    migrateResult.setLoadBalancers(targetLoadBalancers);
    migrateResult.setSecurityGroups(targetSecurityGroups);
    return migrateResult;
  }

  default BasicAmazonDeployDescription generateDescription(ServerGroupLocation source, BasicAmazonDeployDescription deployDescription) {
    return getBasicAmazonDeployHandler().copySourceAttributes(
      getRegionScopedProviderFactory().forRegion(source.getCredentials(), source.getRegion()), source.getName(),
      false, deployDescription);
  }

  default Source getSource(ServerGroupLocation source) {
    Source deploySource = new Source();
    deploySource.setAccount(source.getCredentialAccount());
    deploySource.setRegion(source.getRegion());
    deploySource.setAsgName(source.getName());
    return deploySource;
  }

  default Capacity getCapacity() {
    Capacity capacity = new Capacity();
    capacity.setMin(0);
    capacity.setMax(0);
    capacity.setDesired(0);
    return capacity;
  }

  default MigrateSecurityGroupResult generateAppSecurityGroup(ServerGroupLocation source, ServerGroupLocation target, SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup, boolean dryRun) {
    Names names = Names.parseName(source.getName());
    SecurityGroupLocation appGroupLocation = new SecurityGroupLocation();
    appGroupLocation.setName(names.getApp());
    appGroupLocation.setRegion(source.getRegion());
    appGroupLocation.setCredentials(source.getCredentials());
    appGroupLocation.setVpcId(source.getVpcId());
    SecurityGroupMigrator migrator = new SecurityGroupMigrator(sourceLookup, targetLookup, getMigrateSecurityGroupStrategy(),
      appGroupLocation, new SecurityGroupLocation(target));
    migrator.setCreateIfSourceMissing(true);
    return migrator.migrate(dryRun);
  }

  default MigrateSecurityGroupResult getMigrateSecurityGroupResult(ServerGroupLocation source, ServerGroupLocation target, SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup, boolean dryRun, String group) {
    SecurityGroupLocation sourceLocation = new SecurityGroupLocation();
    sourceLocation.setName(group);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setCredentials(source.getCredentials());
    sourceLocation.setVpcId(source.getVpcId());
    return new SecurityGroupMigrator(sourceLookup, targetLookup, getMigrateSecurityGroupStrategy(),
      sourceLocation, new SecurityGroupLocation(target)).migrate(dryRun);
  }

  default MigrateLoadBalancerResult getMigrateLoadBalancerResult(ServerGroupLocation source, ServerGroupLocation target, SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup, String subnetType, boolean dryRun, String lbName) {
    Names names = Names.parseName(source.getName());
    LoadBalancerLocation sourceLocation = new LoadBalancerLocation();
    sourceLocation.setName(lbName);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setVpcId(source.getVpcId());
    sourceLocation.setCredentials(source.getCredentials());
    return new LoadBalancerMigrator(sourceLookup, targetLookup, getAmazonClientProvider(), getRegionScopedProviderFactory(),
      getMigrateSecurityGroupStrategy(), getDeployDefaults(), getMigrateLoadBalancerStrategy(), sourceLocation,
      new LoadBalancerLocation(target), subnetType, names.getApp()).migrate(dryRun);
  }
}
