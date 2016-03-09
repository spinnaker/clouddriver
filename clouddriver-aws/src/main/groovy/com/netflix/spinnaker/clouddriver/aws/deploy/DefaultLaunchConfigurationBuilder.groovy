/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.InstanceMonitoring
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.aws.deploy.LaunchConfigurationBuilder.LaunchConfigurationSettings
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import groovy.transform.CompileStatic
import org.apache.commons.codec.binary.Base64
import org.joda.time.LocalDateTime

import java.util.regex.Pattern

@CompileStatic
class DefaultLaunchConfigurationBuilder implements LaunchConfigurationBuilder {
  private static final Pattern SG_PATTERN = Pattern.compile(/^sg-[0-9a-f]+$/)

  final AmazonAutoScaling autoScaling
  final AsgService asgService
  final SecurityGroupService securityGroupService
  final List<UserDataProvider> userDataProviders
  final LocalFileUserDataProperties localFileUserDataProperties

  DefaultLaunchConfigurationBuilder(AmazonAutoScaling autoScaling, AsgService asgService,
                                    SecurityGroupService securityGroupService, List<UserDataProvider> userDataProviders,
                                    LocalFileUserDataProperties localFileUserDataProperties) {
    this.autoScaling = autoScaling
    this.asgService = asgService
    this.securityGroupService = securityGroupService
    this.userDataProviders = (userDataProviders ?: Collections.<UserDataProvider>emptyList()) as List<UserDataProvider>
    this.localFileUserDataProperties = localFileUserDataProperties
  }

  /**
   * Extracts the LaunchConfigurationSettings from an existing LaunchConfiguration.
   * @param account the account in which to find the launch configuration
   * @param region the region in which to find the launch configuration
   * @param launchConfigurationName the name of the launch configuration
   * @return LaunchConfigurationSettings for the launch configuration
   */
  LaunchConfigurationSettings buildSettingsFromLaunchConfiguration(AccountCredentials<?> account, String region, String launchConfigurationName) {
    LaunchConfiguration lc = asgService.getLaunchConfiguration(launchConfigurationName)

    String baseName = lc.launchConfigurationName
    String suffix = null
    int suffixLoc = lc.launchConfigurationName.lastIndexOf('-')
    if (suffixLoc != -1) {
      baseName = lc.launchConfigurationName.substring(0, suffixLoc)
      suffix = lc.launchConfigurationName.substring(suffixLoc + 1)
    }

    List<AmazonBlockDevice> blockDevices = lc.blockDeviceMappings.collect { BlockDeviceMapping mapping ->
      if (mapping.ebs) {
        new AmazonBlockDevice(deviceName: mapping.deviceName,
          size: mapping.ebs.volumeSize,
          volumeType: mapping.ebs.volumeType,
          deleteOnTermination: mapping.ebs.deleteOnTermination,
          iops: mapping.ebs.iops,
          snapshotId: mapping.ebs.snapshotId)
      } else {
        new AmazonBlockDevice(deviceName: mapping.deviceName, virtualName: mapping.virtualName)
      }
    }

    /*
      Copy over the original user data only if the UserDataProviders behavior is disabled.
      This is to avoid having duplicate user data.
     */
    String base64UserData = (localFileUserDataProperties && !localFileUserDataProperties.enabled) ? lc.userData : null

    new LaunchConfigurationSettings(
      account: account.name,
      environment: account.environment,
      accountType: account.accountType,
      region: region,
      baseName: baseName,
      suffix: suffix,
      ami: lc.imageId,
      iamRole: lc.iamInstanceProfile,
      classicLinkVpcId: lc.classicLinkVPCId,
      classicLinkVpcSecurityGroups: lc.classicLinkVPCSecurityGroups,
      instanceType: lc.instanceType,
      keyPair: lc.keyName,
      associatePublicIpAddress: lc.associatePublicIpAddress,
      kernelId: lc.kernelId ?: null,
      ramdiskId: lc.ramdiskId ?: null,
      ebsOptimized: lc.ebsOptimized,
      spotPrice: lc.spotPrice,
      instanceMonitoring: lc.instanceMonitoring == null ? false : lc.instanceMonitoring.enabled,
      blockDevices: blockDevices,
      securityGroups: lc.securityGroups,
      base64UserData: base64UserData
    )
  }

  /**
   * Constructs an LaunchConfiguration with the provided settings
   * @param application the name of the application - used to construct a default security group if none are present
   * @param subnetType the subnet type for security groups in the launch configuration
   * @param settings the settings for the launch configuration
   * @return the name of the new launch configuration
   */
  String buildLaunchConfiguration(String application, String subnetType, LaunchConfigurationSettings settings) {
    if (settings.suffix == null) {
      settings = settings.copyWith(suffix: createDefaultSuffix())
    }

    List<String> securityGroupIds = resolveSecurityGroupIds(settings.securityGroups, subnetType)
    settings = settings.copyWith(securityGroups: securityGroupIds)

    if (settings.classicLinkVpcSecurityGroups) {
      if (!settings.classicLinkVpcId) {
        throw new IllegalStateException("Can't provide classic link security groups without classiclink vpc Id")
      }
      List<String> classicLinkIds = resolveSecurityGroupIdsInVpc(settings.classicLinkVpcSecurityGroups, settings.classicLinkVpcId)
      settings = settings.copyWith(classicLinkVpcSecurityGroups: classicLinkIds)
    }

    String name = createName(settings)
    String userData = getUserData(
      settings.baseName,
      name,
      settings.region,
      settings.account,
      settings.environment,
      settings.accountType,
      settings.base64UserData ?: "")
    createLaunchConfiguration(name, userData, settings)
  }

  private String createDefaultSuffix() {
    new LocalDateTime().toString("MMddYYYYHHmmss")
  }

  private String createName(LaunchConfigurationSettings settings) {
    createName(settings.baseName, settings.suffix)
  }

  private String createName(String baseName, String suffix) {
    StringBuilder name = new StringBuilder(baseName)
    if (suffix) {
      name.append('-').append(suffix)
    }
    name.toString()
  }

  private List<String> resolveSecurityGroupIdsByStrategy(List<String> securityGroupNamesAndIds, Closure<Map<String, String>> nameResolver) {
    if (securityGroupNamesAndIds) {
      Collection<String> names = securityGroupNamesAndIds.toSet()
      Collection<String> ids = names.findAll { SG_PATTERN.matcher(it).matches() } as Set<String>
      names.removeAll(ids)
      if (names) {
        def resolvedIds = nameResolver.call(names.toList())
        ids.addAll(resolvedIds.values())
      }
      return ids.toList()
    } else {
      return []
    }
  }

  private List<String> resolveSecurityGroupIds(List<String> securityGroupNamesAndIds, String subnetType) {
    return resolveSecurityGroupIdsByStrategy(securityGroupNamesAndIds) { List<String> names ->
      securityGroupService.getSecurityGroupIdsWithSubnetPurpose(names, subnetType)
    }
  }

  private List<String> resolveSecurityGroupIdsInVpc(List<String> securityGroupNamesAndIds, String vpcId) {
    return resolveSecurityGroupIdsByStrategy(securityGroupNamesAndIds) { List<String> names ->
      securityGroupService.getSecurityGroupIds(names, vpcId)
    }
  }

  private String getUserData(String asgName, String launchConfigName, String region, String account, String environment, String accountType, String base64UserData) {
    String data = userDataProviders?.collect { udp ->
      udp.getUserData(asgName, launchConfigName, region, account, environment, accountType)
    }?.join("\n")
    String userDataDecoded = new String(base64UserData.decodeBase64())
    data = [data, userDataDecoded].findResults { it }.join("\n")
    if (data && data.startsWith("\n")) {
      data = data.substring(1)
    }
    data ? new String(Base64.encodeBase64(data.bytes)) : null
  }

  private String createLaunchConfiguration(String name, String userData, LaunchConfigurationSettings settings) {

    CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest()
      .withImageId(settings.ami)
      .withIamInstanceProfile(settings.iamRole)
      .withLaunchConfigurationName(name)
      .withUserData(userData)
      .withInstanceType(settings.instanceType)
      .withSecurityGroups(settings.securityGroups)
      .withKeyName(settings.keyPair)
      .withAssociatePublicIpAddress(settings.associatePublicIpAddress)
      .withKernelId(settings.kernelId)
      .withRamdiskId(settings.ramdiskId)
      .withEbsOptimized(settings.ebsOptimized)
      .withSpotPrice(settings.spotPrice)
      .withClassicLinkVPCId(settings.classicLinkVpcId)
      .withClassicLinkVPCSecurityGroups(settings.classicLinkVpcSecurityGroups)

    if (settings.instanceMonitoring) {
      request.withInstanceMonitoring(new InstanceMonitoring(enabled: settings.instanceMonitoring))
    }

    if (settings.blockDevices) {
      def mappings = []
      for (blockDevice in settings.blockDevices) {
        def mapping = new BlockDeviceMapping(deviceName: blockDevice.deviceName)
        if (blockDevice.virtualName) {
          mapping.withVirtualName(blockDevice.virtualName)
        } else {
          def ebs = new Ebs()
          blockDevice.with {
            ebs.withVolumeSize(size)
            if (deleteOnTermination != null) {
              ebs.withDeleteOnTermination(deleteOnTermination)
            }
            if (volumeType) {
              ebs.withVolumeType(volumeType)
            }
            if (iops) {
              ebs.withIops(iops)
            }
            if (snapshotId) {
              ebs.withSnapshotId(snapshotId)
            }
          }
          mapping.withEbs(ebs)
        }
        mappings << mapping
      }
      request.withBlockDeviceMappings(mappings)
    }

    autoScaling.createLaunchConfiguration(request)

    name
  }
}
