package com.netflix.spinnaker.clouddriver.tencent.deploy;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentCluster;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class TencentServerGroupNameResolver extends AbstractServerGroupNameResolver {
  public TencentServerGroupNameResolver(
      String accountName,
      String region,
      TencentClusterProvider tencentClusterProvider,
      TencentNamedAccountCredentials credentials) {
    this.accountName = accountName;
    this.region = region;
    this.tencentClusterProvider = tencentClusterProvider;
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(accountName)
            .withResource(TencentBasicResource.class);
    this.autoScalingClient =
        new AutoScalingClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);
  }

  @Override
  public String getPhase() {
    return TENCENT_PHASE;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public List<TakenSlot> getTakenSlots(final String clusterName) {
    String applicationName = Names.parseName(clusterName).getApp();
    TencentCluster cluster =
        tencentClusterProvider.getCluster(applicationName, accountName, clusterName);
    if (cluster == null) {
      return new ArrayList<TakenSlot>();
    } else {
      List<AutoScalingGroup> autoScalingGroups = autoScalingClient.getAllAutoScalingGroups();
      List<AutoScalingGroup> serverGroupsInCluster =
          autoScalingGroups.stream()
              .filter(
                  it -> {
                    return Names.parseName(it.getAutoScalingGroupName())
                        .getCluster()
                        .equals(clusterName);
                  })
              .collect(Collectors.toList());

      return serverGroupsInCluster.stream()
          .map(
              it -> {
                String name = it.getAutoScalingGroupName();
                Date date = AutoScalingClient.ConvertIsoDateTime(it.getCreatedTime());
                return new TakenSlot(name, Names.parseName(name).getSequence(), date);
              })
          .collect(Collectors.toList());
    }
  }

  private static final String TENCENT_PHASE = "TENCENT_DEPLOY";
  private final String accountName;
  private final String region;
  private final TencentClusterProvider tencentClusterProvider;
  private final AutoScalingClient autoScalingClient;
  private final Namer namer;
}
