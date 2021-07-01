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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.google.common.annotations.VisibleForTesting
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgConfigHelper
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils
import com.netflix.spinnaker.clouddriver.aws.deploy.InstanceTypeUtils.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.ResolvedAmiResult
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerLookupHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy.ScalingPolicyCopier
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import java.util.regex.Pattern

@Slf4j
class BasicAmazonDeployHandler implements DeployHandler<BasicAmazonDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"
  private static final String SUBNET_ID_OVERRIDE_TAG = "SPINNAKER_SUBNET_ID_OVERRIDE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RegionScopedProviderFactory regionScopedProviderFactory
  private final CredentialsRepository<NetflixAmazonCredentials> accountCredentialsRepository
  private final AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider
  private final AwsConfiguration.DeployDefaults deployDefaults
  private final ScalingPolicyCopier scalingPolicyCopier
  private final BlockDeviceConfig blockDeviceConfig
  private final DynamicConfigService dynamicConfigService

  private List<CreateServerGroupEvent> deployEvents = []

  BasicAmazonDeployHandler(RegionScopedProviderFactory regionScopedProviderFactory,
                           CredentialsRepository<NetflixAmazonCredentials> accountCredentialsRepository,
                           AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider,
                           AwsConfiguration.DeployDefaults deployDefaults,
                           ScalingPolicyCopier scalingPolicyCopier,
                           BlockDeviceConfig blockDeviceConfig,
                           DynamicConfigService dynamicConfigService) {
    this.regionScopedProviderFactory = regionScopedProviderFactory
    this.accountCredentialsRepository = accountCredentialsRepository
    this.amazonServerGroupProvider = amazonServerGroupProvider
    this.deployDefaults = deployDefaults
    this.scalingPolicyCopier = scalingPolicyCopier
    this.blockDeviceConfig = blockDeviceConfig
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description, List priorOutputs) {
    def deploymentResult = new DeploymentResult()

    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."

    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key

      def sourceRegionScopedProvider = buildSourceRegionScopedProvider(task, description.source)

      description = copySourceAttributes(
        sourceRegionScopedProvider, description.source.asgName, description.source.useSourceCapacity, description
      )

      List<String> availabilityZones = entry.value

      // Get the properly typed version of the description's subnetType
      def subnetType = description.subnetType

      // Get the list of classic load balancers that were created as part of this conglomerate job to apply to the ASG.
      List<UpsertAmazonLoadBalancerResult.LoadBalancer> suppliedLoadBalancers = (List<UpsertAmazonLoadBalancerResult.LoadBalancer>) priorOutputs.findAll {
        it instanceof UpsertAmazonLoadBalancerResult
      }?.loadBalancers?.getAt(region)

      if (!description.loadBalancers) {
        description.loadBalancers = []
      }
      description.loadBalancers.addAll (suppliedLoadBalancers?.name ?: [])

      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)

      def loadBalancers = loadBalancerLookupHelper().getLoadBalancersByName(regionScopedProvider, description.loadBalancers)
      if (loadBalancers.unknownLoadBalancers) {
        throw new IllegalStateException("Unable to find classic load balancers named $loadBalancers.unknownLoadBalancers")
      }

      def targetGroups = targetGroupLookupHelper().getTargetGroupsByName(regionScopedProvider, description.targetGroups)
      if (targetGroups.unknownTargetGroups) {
        throw new IllegalStateException("Unable to find target groups named $targetGroups.unknownTargetGroups")
      }

      def amazonEC2 = regionScopedProvider.amazonEC2

      String classicLinkVpcId = null
      List<String> classicLinkVpcSecurityGroups = null
      if (!subnetType) {
        def result = amazonEC2.describeVpcClassicLink()
        classicLinkVpcId = result.vpcs.find { it.classicLinkEnabled }?.vpcId
        if (classicLinkVpcId) {
          Set<String> classicLinkGroupNames = []
          classicLinkGroupNames.addAll(description.classicLinkVpcSecurityGroups ?: [])
          if (deployDefaults.classicLinkSecurityGroupName) {
            classicLinkGroupNames.addAll(deployDefaults.classicLinkSecurityGroupName)
          }

          // if we have provided groups and a vpcId, resolve them back to names to handle the case of cloning
          // from a Server Group in a different region
          if (description.classicLinkVpcId && description.classicLinkVpcSecurityGroups) {
            def groupIds = classicLinkGroupNames.findAll { it.matches(~/sg-[0-9a-f]+/) } ?: []
            classicLinkGroupNames.removeAll(groupIds)
            if (groupIds) {
              def describeSG = new DescribeSecurityGroupsRequest().withGroupIds(groupIds)
              def provider = sourceRegionScopedProvider ?: regionScopedProvider
              def resolvedNames = provider.amazonEC2.describeSecurityGroups(describeSG).securityGroups.findResults {
                if (it.vpcId == description.classicLinkVpcId && groupIds.contains(it.groupId)) {
                  return it.groupName
                }
                return null
              } ?: []

              if (resolvedNames.size() != groupIds.size()) {
                throw new IllegalStateException("failed to look up classic link security groups, had $groupIds found $resolvedNames")
              }
              classicLinkGroupNames.addAll(resolvedNames)
            }
          }

          if (deployDefaults.addAppGroupsToClassicLink) {
            //if we cloned to a new cluster, don't bring along the old clusters groups
            if (description.source) {
              def srcName = Names.parseName(description.source.asgName)
              boolean mismatch = false
              if (srcName.app != description.application) {
                classicLinkGroupNames.remove(srcName.app)
                mismatch = true
              }
              if (srcName.stack && (mismatch || srcName.stack != description.stack)) {
                classicLinkGroupNames.remove("${srcName.app}-${srcName.stack}".toString())
                mismatch = true
              }
              if (srcName.detail && (mismatch || srcName.detail != description.freeFormDetails)) {
                classicLinkGroupNames.remove("${srcName.app}-${srcName.stack ?: ''}-${srcName.detail}")
              }
            }
            def groupNamesToLookUp = []
            if (!classicLinkGroupNames.contains(description.application)) {
              groupNamesToLookUp.add(description.application)
            }
            if (description.stack) {
              String stackGroup = "${description.application}-${description.stack}"
              if (!classicLinkGroupNames.contains(stackGroup)) {
                groupNamesToLookUp.add(stackGroup)
              }
            }
            if (description.freeFormDetails) {
              String clusterGroup = "${description.application}-${description.stack ?: ''}-${description.freeFormDetails}"
              if (!classicLinkGroupNames.contains(clusterGroup)) {
                groupNamesToLookUp.add(clusterGroup)
              }
            }
            if (groupNamesToLookUp && classicLinkGroupNames.size() < deployDefaults.maxClassicLinkSecurityGroups) {
              def appGroups = regionScopedProvider.securityGroupService.getSecurityGroupIds(groupNamesToLookUp, classicLinkVpcId, false)
              for (String name : groupNamesToLookUp) {
                if (appGroups.containsKey(name)) {
                  if (classicLinkGroupNames.size() < deployDefaults.maxClassicLinkSecurityGroups) {
                    classicLinkGroupNames.add(name)
                  } else {
                    task.updateStatus(BASE_PHASE, "Not adding $name to classicLinkVpcSecurityGroups, already have $deployDefaults.maxClassicLinkSecurityGroups groups")
                  }
                }
              }
            }
          }
          classicLinkVpcSecurityGroups = classicLinkGroupNames.toList()
          task.updateStatus(BASE_PHASE, "Attaching $classicLinkGroupNames as classicLinkVpcSecurityGroups")
        }
      }

      ResolvedAmiResult ami = priorOutputs.find({
        it instanceof ResolvedAmiResult && it.region == region && (it.amiName == description.amiName || it.amiId == description.amiName)
      }) ?: AmiIdResolver.resolveAmiIdFromAllSources(amazonEC2, region, description.amiName, description.credentials.accountId)

      if (!ami) {
        throw new IllegalArgumentException("unable to resolve AMI imageId from $description.amiName in $region")
      }
      InstanceTypeUtils.validateCompatibility(ami.virtualizationType, description.getAllInstanceTypes())

      def account = accountCredentialsRepository.getOne(description.credentials.name)
      if (account == null) {
        throw new IllegalArgumentException("Account with name ${description.credentials.name} could not be found.")
      }

      if (description.useAmiBlockDeviceMappings) {
        description.blockDevices = AsgConfigHelper.convertBlockDevices(ami.blockDeviceMappings)
      } else if(description.blockDevices == null){
        // Get default block device mapping for requested instance type.
        // For the case of multiple instance types in request, top-level instance type is used to derive defaults.
        description.blockDevices = blockDeviceConfig.getBlockDevicesForInstanceType(description.instanceType)
      }

      if (description.spotPrice == "") {
        description.spotPrice = null
      }

      def capacity = new DeploymentResult.Deployment.Capacity(
          min: description.capacity.min ?: 0,
          max: description.capacity.max ?: 0,
          desired: description.capacity.desired ?: 0
      )

      def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, dynamicConfigService)

      // build AsgWorker configuration and then call deploy
      def asgConfig = new AutoScalingWorker.AsgConfiguration(
        application: description.application,
        region: region,
        credentials: description.credentials,
        stack: description.stack,
        freeFormDetails: description.freeFormDetails,
        ami: ami.amiId,
        classicLinkVpcId: classicLinkVpcId,
        classicLinkVpcSecurityGroups: classicLinkVpcSecurityGroups,
        minInstances: capacity.min,
        maxInstances: capacity.max,
        desiredInstances: capacity.desired,
        securityGroups: description.securityGroups,
        iamRole: iamRole(description, deployDefaults),
        keyPair: description.keyPair ?: account?.defaultKeyPair,
        sequence: description.sequence,
        ignoreSequence: description.ignoreSequence,
        startDisabled: description.startDisabled,
        associatePublicIpAddress: description.associatePublicIpAddress,
        blockDevices: description.blockDevices,
        instanceType: description.instanceType,
        availabilityZones: availabilityZones,
        subnetType: subnetType,
        subnetIds: description.subnetIds,
        classicLoadBalancers: loadBalancers.classicLoadBalancers,
        targetGroupArns: targetGroups.targetGroupARNs,
        cooldown: description.cooldown,
        enabledMetrics: description.enabledMetrics,
        healthCheckGracePeriod: description.healthCheckGracePeriod,
        healthCheckType: description.healthCheckType,
        terminationPolicies: description.terminationPolicies,
        spotMaxPrice: description.spotPrice,
        suspendedProcesses: description.suspendedProcesses,
        kernelId: description.kernelId,
        ramdiskId: description.ramdiskId,
        instanceMonitoring: description.instanceMonitoring,
        ebsOptimized: description.ebsOptimized == null ? InstanceTypeUtils.getDefaultEbsOptimizedFlag(description.instanceType) : description.ebsOptimized,
        base64UserData: description.base64UserData?.trim(),
        legacyUdf: description.legacyUdf,
        userDataOverride: description.userDataOverride,
        tags: applyAppStackDetailTags(deployDefaults, description).tags,
        blockDeviceTags: description.blockDeviceTags,
        lifecycleHooks: getLifecycleHooks(account, description),
        setLaunchTemplate: description.setLaunchTemplate,
        requireIMDSv2: description.requireIMDSv2,
        enableEnclave: description.enableEnclave,
        associateIPv6Address: description.associateIPv6Address,
        unlimitedCpuCredits: description.unlimitedCpuCredits != null
          ? description.unlimitedCpuCredits
          : getDefaultUnlimitedCpuCredits(description.getAllowedInstanceTypes()),
        placement: description.placement,
        licenseSpecifications: description.licenseSpecifications,
        onDemandAllocationStrategy: description.onDemandAllocationStrategy,
        onDemandBaseCapacity: description.onDemandBaseCapacity,
        onDemandPercentageAboveBaseCapacity: description.onDemandPercentageAboveBaseCapacity,
        spotAllocationStrategy: description.spotAllocationStrategy,
        spotInstancePools: description.spotInstancePools,
        launchTemplateOverridesForInstanceType: description.launchTemplateOverridesForInstanceType,
        capacityRebalance: description.capacityRebalance
      )

      def asgName = autoScalingWorker.deploy(asgConfig)

      deploymentResult.serverGroupNames << "${region}:${asgName}".toString()
      deploymentResult.serverGroupNameByRegion[region] = asgName

      if (description.copySourceScalingPoliciesAndActions && sourceRegionScopedProvider) {
        copyScalingPoliciesAndScheduledActions(
          task, sourceRegionScopedProvider,
          sourceRegionScopedProvider.amazonCredentials, description.credentials,
          description.source.asgName, asgName,
          description.source.region, region
        )
      }

      description.events << new CreateServerGroupEvent(
        AmazonCloudProvider.ID, account.accountId, region, asgName
      )

      deploymentResult.deployments.add(
          new DeploymentResult.Deployment(
              cloudProvider: "aws",
              account: description.getAccount(),
              location: region,
              serverGroupName: asgName,
              capacity: capacity
          )
      )
    }

    task.updateStatus(BASE_PHASE, "Created the following deployments: ${deploymentResult.deployments}")

    return deploymentResult
  }

  @VisibleForTesting
  @PackageScope
  LoadBalancerLookupHelper loadBalancerLookupHelper() {
    return new LoadBalancerLookupHelper()
  }

  @VisibleForTesting
  @PackageScope
  TargetGroupLookupHelper targetGroupLookupHelper() {
    return new TargetGroupLookupHelper()
  }

  @VisibleForTesting
  @PackageScope
  BasicAmazonDeployDescription copySourceAttributes(RegionScopedProviderFactory.RegionScopedProvider sourceRegionScopedProvider,
                                                    String sourceAsgName, Boolean useSourceCapacity,
                                                    BasicAmazonDeployDescription description) {

    def copySourceSubnetIdOverrides = description.copySourceSubnetIdOverrides && !description.subnetIds
    if (copySourceSubnetIdOverrides && sourceRegionScopedProvider && sourceAsgName) {
      // avoid unnecessary AWS calls by fetching a cached copy of the source server group
      def serverGroup = amazonServerGroupProvider.getServerGroup(
        sourceRegionScopedProvider.amazonCredentials?.name,
        sourceRegionScopedProvider.region,
        sourceAsgName
      )

      if (serverGroup) {
        String subnetIdTag = serverGroup.asg.tags.find { it.key == SUBNET_ID_OVERRIDE_TAG }?.value
        if (subnetIdTag) {
          // source server group had subnet id overrides, propagate them forward
          description.subnetIds = subnetIdTag.split(",")
        }
      }
    }

    if (description.subnetIds) {
      /*
       * Ensure the new server group receives the subnet id override tag.
       *
       * These subnet ids will be validated against the region / availability zones and provided subnet type.
       *
       * The deploy will fail (and tag discarded!) if any of the ids are invalid.
       */
      description.tags[SUBNET_ID_OVERRIDE_TAG] = description.subnetIds.join(",")
    }

    description.tags = cleanTags(description.tags)
    description.blockDeviceTags = cleanTags(description.blockDeviceTags)

    // skip a couple of AWS calls if we won't use any of the data
    if (!(useSourceCapacity || description.copySourceCustomBlockDeviceMappings)) {
      return description
    }

    if (!sourceRegionScopedProvider) {
      if (useSourceCapacity) {
        throw new IllegalStateException("useSourceCapacity requested, but no source available")
      }
      return description
    }

    description = description.clone()

    def sourceAutoScaling = sourceRegionScopedProvider.autoScaling
    def ancestorAsgs = sourceAutoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [sourceAsgName])
    ).autoScalingGroups
    def sourceAsg = ancestorAsgs.getAt(0)

    if (!sourceAsg?.launchConfigurationName && sourceAsg?.launchTemplate == null && sourceAsg?.mixedInstancesPolicy == null) {
      if (useSourceCapacity) {
        throw new IllegalStateException("useSourceCapacity requested, but no source ASG found")
      }
      return description
    }

    // capacity
    if (useSourceCapacity) {
      description.capacity.min = sourceAsg.minSize
      description.capacity.max = sourceAsg.maxSize
      description.capacity.desired = sourceAsg.desiredCapacity
    }

    // block device mappings
    if (!description.copySourceCustomBlockDeviceMappings) {
      return description
    } else {
      description.blockDevices = buildBlockDeviceMappingsFromSourceAsg(sourceRegionScopedProvider, sourceAsg, description)
    }

    return description
  }

  @VisibleForTesting
  @PackageScope
  void copyScalingPoliciesAndScheduledActions(Task task,
                                              RegionScopedProviderFactory.RegionScopedProvider sourceRegionScopedProvider,
                                              NetflixAmazonCredentials sourceCredentials,
                                              NetflixAmazonCredentials targetCredentials,
                                              String sourceAsgName,
                                              String targetAsgName,
                                              String sourceRegion,
                                              String targetRegion) {
    if (!sourceRegionScopedProvider) {
      return
    }

    def asgReferenceCopier = sourceRegionScopedProvider.getAsgReferenceCopier(targetCredentials, targetRegion)
    scalingPolicyCopier.copyScalingPolicies(task, sourceAsgName, targetAsgName,
      sourceCredentials, targetCredentials, sourceRegion, targetRegion)
    asgReferenceCopier.copyScheduledActionsForAsg(task, sourceAsgName, targetAsgName)
  }

  @VisibleForTesting
  @PackageScope
  static List<AmazonAsgLifecycleHook> getLifecycleHooks(NetflixAmazonCredentials credentials, BasicAmazonDeployDescription description) {
    List<AmazonAsgLifecycleHook> lifecycleHooks = description.lifecycleHooks ?: []
    if (description.includeAccountLifecycleHooks && credentials.lifecycleHooks?.size() > 0) {
      lifecycleHooks.addAll(credentials.lifecycleHooks.collect {
        new AmazonAsgLifecycleHook(
          roleARN: it.roleARN,
          notificationTargetARN: it.notificationTargetARN,
          lifecycleTransition: AmazonAsgLifecycleHook.Transition.valueOfName(it.lifecycleTransition),
          heartbeatTimeout: it.heartbeatTimeout,
          defaultResult: it.defaultResult ? AmazonAsgLifecycleHook.DefaultResult.valueOf(it.defaultResult) : null
        )
      })
    }
    return lifecycleHooks
  }

  /**
   * Default unlimitedCpuCredits to false if applicable (i.e. burstable performance instance type), and not specified.
   *
   * For the multiple instance types case, the Spinnaker default false is used ONLY if all types support bursting to ensure compatibility with ALL instance types.
   * In such cases, the AWS default comes into play.
   *
   * Reasoning:
   * 1) consistent default cpu credits value for burstable performance instance families
   * AWS default mode if cpu credits is not specified depends on the instance family:
   *    * t2: standard
   *    * t3/t3a: unlimited
   *
   * 2) let users explicitly choose 'unlimited' bursting which could translate to higher instance costs, depending on usage
   */
  @VisibleForTesting
  static Boolean getDefaultUnlimitedCpuCredits(final Set<String> instanceTypes) {

    // return the default, false only if all instance types support bursting
    return InstanceTypeUtils.isBurstingSupportedByAllTypes(instanceTypes) ? false : null
  }

  static String iamRole(BasicAmazonDeployDescription description, DeployDefaults deployDefaults) {
    def iamRole = description.iamRole ?: deployDefaults.iamRole
    return description.application ? iamRole.replaceAll(Pattern.quote('{{application}}'), description.application) : iamRole
  }

  @VisibleForTesting
  @PackageScope
  static Map<String, String> cleanTags(Map<String, String> tags) {
    return tags ? tags.findAll { !it.key.startsWith("aws:") } : [:]
  }

  /**
   * Add tags for application/stack/details iff `deployDefaults.addAppStackDetailTags` is true.
   */
  @VisibleForTesting
  @PackageScope
  static BasicAmazonDeployDescription applyAppStackDetailTags(DeployDefaults deployDefaults,
                                                              BasicAmazonDeployDescription description) {
    if (!deployDefaults.addAppStackDetailTags) {
      return description
    }

    description = description.clone()

    if (description.application) {
      description.tags["spinnaker:application"] = description.application
    } else {
      description.tags.remove("spinnaker:application")
    }


    if (description.stack) {
      description.tags["spinnaker:stack"] = description.stack
    } else {
      description.tags.remove("spinnaker:stack")
    }


    if (description.freeFormDetails) {
      description.tags["spinnaker:details"] = description.freeFormDetails
    } else {
      description.tags.remove("spinnaker:details")
    }

    return description
  }

  private RegionScopedProviderFactory.RegionScopedProvider buildSourceRegionScopedProvider(Task task,
                                                                                           BasicAmazonDeployDescription.Source source) {
    if (source.account && source.region && source.asgName) {
      def sourceRegion = source.region
      def sourceAsgCredentials = accountCredentialsRepository.getOne(source.account)
      def regionScopedProvider = regionScopedProviderFactory.forRegion(sourceAsgCredentials, sourceRegion)

      def sourceAsgs = regionScopedProvider.autoScaling.describeAutoScalingGroups(
        new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [source.asgName])
      )

      if (!sourceAsgs.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "Unable to locate source asg (${source.account}:${source.region}:${source.asgName})"
        return null
      }

      return regionScopedProvider
    }

    return null
  }

  /**
   * Build block device mappings for the request from source ASG.
   * Used when copy from source is requested.
   *
   * @param sourceAsgRegionScopedProvider regionScopedProvider for the source ASG
   * @param sourceAsg source AWS AutoScalingGroup
   * @param newAsgDescription description in request
   * @return a list of {@link AmazonBlockDevice} for the requested configuration
   */
  @VisibleForTesting
  @PackageScope
  List<AmazonBlockDevice> buildBlockDeviceMappingsFromSourceAsg(
    RegionScopedProviderFactory.RegionScopedProvider sourceAsgRegionScopedProvider,
    AutoScalingGroup sourceAsg,
    BasicAmazonDeployDescription newAsgDescription) {

    // if block device mappings are explicitly specified, they should be used regardless of source ASG settings
    if (newAsgDescription.blockDevices != null) {
      return newAsgDescription.blockDevices
    }

    if (newAsgDescription.getInstanceType() != AsgConfigHelper.getTopLevelInstanceTypeForAsg(sourceAsg, sourceAsgRegionScopedProvider)) {
      // If instance type(s) being requested is NOT the same as those in source ASG,
      // get default mapping for the new type ONLY IF that same logic was applied for source ASG.
      // For the case of multiple instance types in request, top-level instance type is used to derive defaults.
      // Top-level instance type is nothing but the description.instanceType
      def blockDevicesForSourceAsg = AsgConfigHelper.getBlockDeviceMappingForAsg(sourceAsg, sourceAsgRegionScopedProvider)
        .collect { [deviceName: it.deviceName, virtualName: it.virtualName, size: it.size] }
        .sort { it.deviceName }

      def defaultBlockDevicesForSourceInsType =
        blockDeviceConfig.getBlockDevicesForInstanceType(AsgConfigHelper.getTopLevelInstanceTypeForAsg(sourceAsg, sourceAsgRegionScopedProvider))
          .collect { [deviceName: it.deviceName, virtualName: it.virtualName, size: it.size] }
          .sort { it.deviceName }

      boolean isDefaultMappingUsedInSourceAsg = blockDevicesForSourceAsg == defaultBlockDevicesForSourceInsType
      if (isDefaultMappingUsedInSourceAsg) {
        return blockDeviceConfig.getBlockDevicesForInstanceType(newAsgDescription.getInstanceType())
      }
    }

    return AsgConfigHelper.getBlockDeviceMappingForAsg(sourceAsg, sourceAsgRegionScopedProvider)
  }
}
