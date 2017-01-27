package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

import java.time.Instant

class DcosServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String DCOS_PHASE = "DCOS_DEPLOY"

  private final DCOS dcosClient
  private String region

  DcosServerGroupNameResolver(DCOS dcosClient, String account, String region) {
    this.dcosClient = dcosClient
    this.region = region ? "/${account}/${region}" : "/${account}"
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
    List<App> apps = dcosClient.getApps(region).apps

    if (!apps) {
      return Collections.emptyList()
    }

    def filteredApps = apps.findAll {
      new DcosSpinnakerId(it.id).service.cluster == Names.parseName(clusterName).cluster
    }

    return filteredApps.collect { App app ->
      final def names = new DcosSpinnakerId(app.id).service
      return new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: names.cluster,
        sequence       : names.sequence,
        createdTime    : new Date(translateTime(app.versionInfo.lastConfigChangeAt))
      )
    }
  }

  static long translateTime(String time) {
    time ? Instant.parse(time).toEpochMilli() : 0
  }
}
