package com.netflix.spinnaker.clouddriver.tencent.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials
import com.netflix.spinnaker.moniker.Namer


class TencentServerGroupNameResolver extends AbstractServerGroupNameResolver{
  private static final String TENCENT_PHASE = "TENCENT_DEPLOY"
  private final String accountName
  private final String region
  private final TencentClusterProvider tencentClusterProvider
  private final AutoScalingClient autoScalingClient
  private final Namer namer

  TencentServerGroupNameResolver(
    String accountName,
    String region,
    TencentClusterProvider tencentClusterProvider,
    TencentNamedAccountCredentials credentials
  ) {
    this.accountName = accountName
    this.region = region
    this.tencentClusterProvider = tencentClusterProvider
    this.namer = NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(accountName)
      .withResource(TencentBasicResource)
    this.autoScalingClient = new AutoScalingClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )
  }

  @Override
  String getPhase() {
    return TENCENT_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def applicationName = Names.parseName(clusterName).app
    def cluster = tencentClusterProvider.getCluster(applicationName, accountName, clusterName)
    if (!cluster) {
      []
    }
    else {
      def autoScalingGroups = autoScalingClient.getAllAutoScalingGroups()
      def serverGroupsInCluster = autoScalingGroups.findAll {
        Names.parseName(it.autoScalingGroupName).cluster == clusterName
      }

      return serverGroupsInCluster.collect {
        def name = it.autoScalingGroupName
        def date = AutoScalingClient.ConvertIsoDateTime(it.createdTime)
        new AbstractServerGroupNameResolver.TakenSlot(
            serverGroupName: name,
            sequence: Names.parseName(name).sequence,
            createdTime: date
          )
      }
    }
  }
}
