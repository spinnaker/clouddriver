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
import org.apache.catalina.Server
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/applications/{application}/serverGroups")
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
  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  ServerGroup getServerGroup(@PathVariable String application, // needed for @PreAuthorize
                             @PathVariable String account,
                             @PathVariable String region,
                             @PathVariable String name) {
    def matches = (Set<ServerGroup>) clusterProviders.findResults { provider ->
      requestQueue.execute(application, { provider.getServerGroup(account, region, name) })
    }
    if (!matches) {
      throw new ServerGroupNotFoundException([name: name, account: account, region: region])
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
      .findResults { ClusterProvider cp -> requestQueue.execute(application, { cp.getClusterDetails(application)?.values() }) }
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
  @RequestMapping(method = RequestMethod.GET)
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

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleServerGroupNotFoundException(ServerGroupNotFoundException ex) {
    def message = messageSource.getMessage("serverGroup.not.found", [ex.name, ex.account, ex.region] as String[], "serverGroup.not.found", LocaleContextHolder.locale)
    [error: "serverGroup.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  static class ServerGroupNotFoundException extends RuntimeException {
    String name
    String account
    String region
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
    Boolean isDisabled
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
      account = cluster.accountName
      region = serverGroup.region
      createdTime = serverGroup.getCreatedTime()
      isDisabled = serverGroup.isDisabled()
      instances = serverGroup.getInstances()?.findResults { it ? new InstanceViewModel(it) : null } ?: []
      instanceCounts = serverGroup.getInstanceCounts()
      securityGroups = serverGroup.getSecurityGroups()
      loadBalancers = serverGroup.getLoadBalancers()
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

