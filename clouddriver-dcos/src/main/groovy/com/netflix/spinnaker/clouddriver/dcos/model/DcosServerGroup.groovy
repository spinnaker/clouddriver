package com.netflix.spinnaker.clouddriver.dcos.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.AppToDeployDcosServerGroupDescriptionMapper
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.Canonical
import mesosphere.marathon.client.model.v2.App

import java.time.Instant
import java.util.regex.Pattern

/**
 * Equivalent of a Dcos {@link mesosphere.marathon.client.model.v2.App}
 */
class DcosServerGroup implements ServerGroup, Serializable {

  private static final HAPROXY_GROUP_PATTERN = Pattern.compile(/^HAPROXY_(\d*_)*GROUP$/)

  App app
  final String type = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  String name
  String group
  String region
  String account
  String json
  String kind
  Double cpus
  Double mem
  Double disk
  Map<String, String> labels = [:]

  DeployDcosServerGroupDescription deployDescription

  Long createdTime
  Set<String> loadBalancers

  @JsonIgnore
  Set<DcosSpinnakerLbId> fullyQualifiedLoadBalancers

  Set<DcosInstance> instances = [] as Set

  DcosServerGroup() {} //default constructor for deserialization

  DcosServerGroup(String name, String region, String account) {
    this.name = name
    this.region = region
    this.account = account
  }

  DcosServerGroup(String account, App app) {
    this.app = app
    this.json = app.toString()
    def id = new DcosSpinnakerAppId(app.id, account)
    this.name = id.serverGroupName.group
    this.region = id.safeRegion
    this.account = id.account
    this.kind = "Application"

    populateLoadBalancers(app)

    this.cpus = app.cpus
    this.mem = app.mem
    this.disk = app.disk
    this.labels = app.labels

    this.createdTime = app.versionInfo?.lastConfigChangeAt ? Instant.parse(app.versionInfo.lastConfigChangeAt).toEpochMilli() : null

    this.deployDescription = AppToDeployDcosServerGroupDescriptionMapper.map(app, account)

    // TODO can't always assume the tasks are present in the App! Depends on API used to retrieve
    this.instances = app.tasks?.collect({ new DcosInstance(it, account) }) as Set ?: []
  }

  void populateLoadBalancers(App app) {
    fullyQualifiedLoadBalancers = app.labels?.findResults { key, val ->
      if (key.matches(HAPROXY_GROUP_PATTERN)) {
        return val.split(",")
      } else {
        return null
      }
    }?.flatten()?.findResults({
      def appId = DcosSpinnakerLbId.from(it.replace('_', '/'))
      DcosProviderUtils.isLoadBalancerIdValid(appId, account) ? appId : null
    })?.toSet() ?: []

    loadBalancers = fullyQualifiedLoadBalancers?.collect { it.loadBalancerName } ?: []
  }

  @Override
  Boolean isDisabled() {
    app.instances <= 0
  }

  @Override
  Set<String> getZones() {
    [] as Set
  }

  @Override
  Set<String> getSecurityGroups() {
    [] as Set
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    [:]
  }

  @Override
  Map<String, Object> getTags() {
    app.labels
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
            total: instances.size(),
            up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
            down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
            unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
            starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
            outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    new ServerGroup.Capacity(min: app.instances, max: app.instances, desired: app.instances)
  }

  // This isn't part of the ServerGroup interface, but I'm pretty sure if this is not here the build info doesn't return
  // to deck correctly (need to test again)
  Map<String, Object> getBuildInfo() {
    def buildInfo = [:]

    def imageDesc = DcosProviderUtils.buildImageDescription(app.container?.docker?.image)

    buildInfo.imageDesc = imageDesc
    buildInfo.images = imageDesc ? ["$imageDesc.repository:$imageDesc.tag".toString()] : []

    return buildInfo
  }

  @Override
  ServerGroup.ImagesSummary getImagesSummary() {
    def bi = buildInfo

    def parts = bi.imageDesc.repository.split("/")

    return new ServerGroup.ImagesSummary() {
      @Override
      List<? extends ServerGroup.ImageSummary> getSummaries() {
        [new ServerGroup.ImageSummary() {
          String serverGroupName = name
          String imageName = "${parts[0]}-${parts[1]}".toString()
          String imageId = app.container?.docker?.image

          @Override
          Map<String, Object> getBuildInfo() {
            bi
          }

          @Override
          Map<String, Object> getImage() {
            return [
                    container : imageName,
                    registry  : bi.imageDesc.registry,
                    tag       : bi.imageDesc.tag,
                    repository: bi.imageDesc.repository,
                    imageId   : imageId
            ]
          }
        }]
      }
    }
  }

  @Canonical
  static class ImageDescription {
    String repository
    String tag
    String registry
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }
}
