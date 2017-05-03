package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse

import java.time.Instant

class DcosServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String DCOS_PHASE = "DCOS_DEPLOY"

  private final DCOS dcosClient
  private String region

  DcosServerGroupNameResolver(DCOS dcosClient, String account, String region, String group) {
    this.dcosClient = dcosClient
    this.region = group ? "/${account}/${region}/${group}" : "/${account}/${region}"
  }

  @Override
  String getPhase() {
    return DCOS_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    Optional<GetAppNamespaceResponse> appNamespaceResponse = dcosClient.maybeApps(region)
    List<App> apps = appNamespaceResponse != null && appNamespaceResponse.isPresent() ? appNamespaceResponse.get().apps : []

    if (!apps) {
      return []
    }

    def filteredApps = apps.findAll {
      def appId = DcosSpinnakerAppId.parseVerbose(it.id)
      appId.isPresent() && appId.get().namespace == region && appId.get().serverGroupName.cluster == Names.parseName(clusterName).cluster
    }

    return filteredApps.collect { App app ->
      final def names = DcosSpinnakerAppId.parseVerbose(app.id).get().serverGroupName
      return new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: names.cluster,
        sequence       : names.sequence,
        createdTime    : new Date(translateTime(app.version))
      )
    }
  }

  static long translateTime(String time) {
    time ? Instant.parse(time).toEpochMilli() : 0
  }
}
