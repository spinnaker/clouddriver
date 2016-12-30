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
