package com.netflix.spinnaker.clouddriver.dcos.deploy

import java.text.SimpleDateFormat

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver.TakenSlot

import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

class DcosServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String DCOS_PHASE = "DCOS_DEPLOY"

  private final DCOS dcosClient
  final String region

  DcosServerGroupNameResolver(DCOS dcosClient, String region) {
    this.dcosClient = dcosClient
    this.region = region
  }

  @Override
  String getPhase() {
    return DCOS_PHASE
  }

  @Override
  List<TakenSlot> getTakenSlots(String clusterName) {
    List<App> apps = dcosClient.apps.apps

    return apps.collect { App app ->
      return new TakenSlot(
        serverGroupName: app.id,
        sequence       : Names.parseName(app.id).sequence,
        createdTime    : new Date(translateTime(app.versionInfo.lastConfigChangeAt))
      )
    }
  }

  static long translateTime(String time) {
    time ? (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(time)).getTime() : 0
  }
}
