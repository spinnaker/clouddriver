/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.api.services.compute.model.AutoscalingPolicy
import com.google.api.services.compute.model.InstanceGroupManagerActionsSummary
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.Canonical

@Canonical
class GoogleServerGroup {

  String name
  String region
  String zone
  Set<GoogleInstance> instances = []
  Set health = []
  Map<String, Object> launchConfig = [:]
  Map<String, Object> asg = [:]
  Set<String> securityGroups = []
  Map buildInfo
  Boolean disabled = false
  String networkName
  Set<String> instanceTemplateTags = []
  String selfLink
  InstanceGroupManagerActionsSummary currentActions

  @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
  AutoscalingPolicy autoscalingPolicy

  // Non-serialized values built up by providers
  @JsonIgnore
  Set<GoogleLoadBalancer> loadBalancers = []

  @JsonIgnore
  View getView() {
    new View()
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Canonical
  class View implements ServerGroup {
    final String type = GoogleCloudProvider.GCE

    String name = GoogleServerGroup.this.name
    String region = GoogleServerGroup.this.region
    String zone = GoogleServerGroup.this.zone
    Set<String> zones = [GoogleServerGroup.this.zone]
    Set<GoogleInstance.View> instances = GoogleServerGroup.this.instances.collect { it?.view }
    Map<String, Object> asg = GoogleServerGroup.this.asg
    Map<String, Object> launchConfig = GoogleServerGroup.this.launchConfig
    Set<String> securityGroups = GoogleServerGroup.this.securityGroups
    Boolean disabled = GoogleServerGroup.this.disabled
    String networkName = GoogleServerGroup.this.networkName
    Set<String> instanceTemplateTags = GoogleServerGroup.this.instanceTemplateTags
    String selfLink = GoogleServerGroup.this.selfLink
    InstanceGroupManagerActionsSummary currentActions = GoogleServerGroup.this.currentActions
    AutoscalingPolicy autoscalingPolicy = GoogleServerGroup.this.autoscalingPolicy

    @Override
    Boolean isDisabled() { // Because groovy isn't smart enough to generate this method :-(
      disabled
    }

    @Override
    Long getCreatedTime() {
      launchConfig ? launchConfig.createdTime as Long : null
    }

    @Override
    ServerGroup.Capacity getCapacity() {
      def asg = GoogleServerGroup.this.asg
      asg ?
          new ServerGroup.Capacity(min: asg.minSize ? asg.minSize as Integer : 0,
                                   max: asg.maxSize ? asg.maxSize as Integer : 0,
                                   desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0) :
          null
    }

    @Override
    Set<String> getLoadBalancers() {
      Set<String> loadBalancerNames = []
      def asg = GoogleServerGroup.this.asg
      if (asg && asg.containsKey("loadBalancerNames")) {
        loadBalancerNames = (Set<String>) asg.loadBalancerNames
      }
      return loadBalancerNames
    }

    @Override
    ServerGroup.ImagesSummary getImagesSummary() {
      def bi = GoogleServerGroup.this.buildInfo
      return new ServerGroup.ImagesSummary() {
        @Override
        List<ServerGroup.ImageSummary> getSummaries() {
          return [new ServerGroup.ImageSummary() {
            String serverGroupName = name
            String imageName = launchConfig?.instanceTemplate?.name
            String imageId = launchConfig?.imageId

            @Override
            Map<String, Object> getBuildInfo() {
              return bi
            }

            @Override
            Map<String, Object> getImage() {
              return launchConfig?.instanceTemplate
            }
          }]
        }
      }
    }

    @Override
    ServerGroup.ImageSummary getImageSummary() {
      imagesSummary?.summaries?.get(0)
    }

    @Override
    ServerGroup.InstanceCounts getInstanceCounts() {
      new ServerGroup.InstanceCounts(
          total: instances.size(),
          up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
          down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
          unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
          starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
          outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
      )
    }

    static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
      instances.findAll { Instance it -> it.getHealthState() == healthState }
    }
  }
}
