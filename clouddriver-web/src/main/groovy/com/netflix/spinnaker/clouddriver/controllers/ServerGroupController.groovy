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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.model.InstanceTargetGroups
import com.netflix.spinnaker.clouddriver.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.model.view.ServerGroupViewModelPostProcessor
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.moniker.Moniker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ServerGroupController {

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  MessageSource messageSource

  @Autowired
  RequestQueue requestQueue

  @Autowired(required = false)
  ServerGroupViewModelPostProcessor serverGroupViewModelPostProcessor

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') and hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/applications/{application}/serverGroups/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  ServerGroup getServerGroupByApplication(@PathVariable String application, // needed for @PreAuthorize
                                          @PathVariable String account,
                                          @PathVariable String region,
                                          @PathVariable String name) {
    getServerGroup(account, region, name)
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @PostAuthorize("hasPermission(returnObject?.moniker?.app, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/serverGroups/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  // TODO: /application and /serverGroup endpoints should be in their own controllers. See https://github.com/spinnaker/spinnaker/issues/2023
  ServerGroup getServerGroupByMoniker(@PathVariable String account,
                                      @PathVariable String region,
                                      @PathVariable String name) {
    getServerGroup(account, region, name)
  }

  private getServerGroup(String account,
                         String region,
                         String name) {
    def matches = (Set<ServerGroup>) clusterProviders.findResults { provider ->
      requestQueue.execute(name, { provider.getServerGroup(account, region, name) })
    }
    if (!matches) {
      throw new NotFoundException("Server group not found (account: ${account}, region: ${region}, name: ${name})")
    }
    ServerGroup serverGroup = matches.first()
    if (serverGroupViewModelPostProcessor?.supports(serverGroup)) {
      serverGroupViewModelPostProcessor.process(serverGroup)
    }
    serverGroup
  }

  List<Map> expandedList(String application, String cloudProvider) {
    return clusterProviders
      .findAll { cloudProvider ? cloudProvider.equalsIgnoreCase(it.cloudProviderId) : true }
      .findResults { ClusterProvider cp ->
      requestQueue.execute(application, {
        cp.getClusterDetails(application)?.values()
      })
    }
    .collectNested { Cluster c ->
      c.serverGroups?.collect {
        expanded(it, c)
      } ?: []
    }.flatten()
  }

  Map expanded(ServerGroup serverGroup, Cluster cluster) {
    Map sg = objectMapper.convertValue(serverGroup, Map)
    sg.accountName = cluster.accountName
    def name = Names.parseName(cluster.name)
    sg.cluster = name.cluster
    sg.application = name.app
    sg.stack = name.stack
    sg.freeFormDetail = name.detail
    return sg
  }

  List<ServerGroupViewModel> summaryList(String application, String cloudProvider) {

    List<ServerGroupViewModel> serverGroupViews = []

    def clusters = (Set<Cluster>) clusterProviders
      .findAll { cloudProvider ? cloudProvider.equalsIgnoreCase(it.cloudProviderId) : true }
      .findResults { provider ->
      requestQueue.execute(application, { provider.getClusterDetails(application)?.values() })
    }.flatten()
    clusters.each { Cluster cluster ->
      cluster.serverGroups.each { ServerGroup serverGroup ->
        serverGroupViews << new ServerGroupViewModel(serverGroup, cluster)
      }
    }

    serverGroupViews
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/applications/{application}/serverGroups", method = RequestMethod.GET)
  List list(@PathVariable String application,
            @RequestParam(required = false, value = 'expand', defaultValue = 'false') String expand,
            @RequestParam(required = false, value = 'cloudProvider') String cloudProvider,
            @RequestParam(required = false, value = 'clusters') Collection<String> clusters) {

    Boolean isExpanded = Boolean.valueOf(expand)
    if (clusters) {
      return buildSubsetForClusters(clusters, application, isExpanded)
    }
    if (clusters?.empty) {
      return []
    }
    if (isExpanded) {
      return expandedList(application, cloudProvider)
    }
    return summaryList(application, cloudProvider)
  }

  @PostFilter("hasPermission(filterObject?.application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/serverGroups", method = RequestMethod.GET)
  List getServerGroupsByApplications(@RequestParam(value = 'applications') List<String> applications,
                                     @RequestParam(required = false, value = 'cloudProvider') String cloudProvider) {
    applications.collectMany { summaryList(it, cloudProvider) }
  }

  private Collection buildSubsetForClusters(Collection<String> clusters, String application, Boolean isExpanded) {
    Collection<Cluster> matches = clusters.findResults { accountAndName ->
      def (account, clusterName) = accountAndName.split(':')
      if (account && clusterName) {
        return clusterProviders.findResults { clusterProvider ->
          requestQueue.execute(application, { clusterProvider.getCluster(application, account, clusterName) })
        }
      }
      return null
    }.flatten()
    return matches.findResults { cluster ->
      cluster.serverGroups.collect {
        isExpanded ? expanded(it, cluster) : new ServerGroupViewModel(it, cluster)
      }
    }.flatten()
  }

  static class ServerGroupViewModel {
    String name
    String account
    String region
    String cluster
    String vpcId
    String type
    String cloudProvider
    String instanceType
    String application
    Boolean isDisabled
    Moniker moniker
    Map buildInfo
    Long createdTime
    List<InstanceViewModel> instances
    Set<String> loadBalancers
    Set<String> targetGroups
    Set<String> securityGroups
    ServerGroup.InstanceCounts instanceCounts
    Map<String, Object> tags
    Map providerMetadata

    ServerGroupViewModel(ServerGroup serverGroup, Cluster cluster) {
      this.cluster = cluster.name
      type = serverGroup.type
      cloudProvider = serverGroup.cloudProvider
      name = serverGroup.name
      application = Names.parseName(serverGroup.name).getApp()
      account = cluster.accountName
      region = serverGroup.region
      createdTime = serverGroup.getCreatedTime()
      isDisabled = serverGroup.isDisabled()
      instances = serverGroup.getInstances()?.findResults { it ? new InstanceViewModel(it) : null } ?: []
      instanceCounts = serverGroup.getInstanceCounts()
      securityGroups = serverGroup.getSecurityGroups()
      loadBalancers = serverGroup.getLoadBalancers()
      moniker = serverGroup.getMoniker()
      if (serverGroup.launchConfig) {
        if (serverGroup.launchConfig.instanceType) {
          instanceType = serverGroup.launchConfig.instanceType
        }
      }
      if (serverGroup.tags) {
        tags = serverGroup.tags
      }

      if (serverGroup.hasProperty("buildInfo")) {
        buildInfo = serverGroup.buildInfo
      }
      if (serverGroup.hasProperty("vpcId")) {
        vpcId = serverGroup.vpcId
      }
      if (serverGroup.hasProperty("providerMetadata")) {
        providerMetadata = serverGroup.providerMetadata
      }
      if (serverGroup.hasProperty("targetGroups")) {
        targetGroups = serverGroup.targetGroups
      }
    }
  }

  static class InstanceViewModel {
    String id
    List<Map<String, Object>> health
    String healthState
    Long launchTime
    String availabilityZone

    InstanceViewModel(Instance instance) {
      id = instance.name
      healthState = instance.getHealthState().toString()
      launchTime = instance.getLaunchTime()
      availabilityZone = instance.getZone()
      health = instance.health.collect { health ->
        Map healthMetric = [type: health.type]
        if (health.containsKey("state")) {
          healthMetric.state = health.state.toString()
        }
        if (health.containsKey("status")) {
          healthMetric.status = health.status
        }
        if (health.type == InstanceLoadBalancers.HEALTH_TYPE && health.containsKey("loadBalancers")) {
          healthMetric.loadBalancers = health.loadBalancers.collect {
            [name: it.loadBalancerName, state: it.state, description: it.description, healthState: it.healthState]
          }
        }
        if (health.type == InstanceTargetGroups.HEALTH_TYPE && health.containsKey("targetGroups")) {
          healthMetric.targetGroups = health.targetGroups.collect {
            [name: it.targetGroupName, state: it.state, description: it.description, healthState: it.healthState]
          }
        }
        healthMetric
      }
    }
  }

}

