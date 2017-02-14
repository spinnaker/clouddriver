package com.netflix.spinnaker.clouddriver.dcos.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
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

  Long createdTime
  Set<String> loadBalancers

  @JsonIgnore
  Set<DcosSpinnakerId> fullyQualifiedLoadBalancers

  Set<DcosInstance> instances = [] as Set

  DcosServerGroup() {}

  DcosServerGroup(String name, String region, String account) {
    this.name = name
    this.region = region
    this.account = account
  } //default constructor for deserialization

  DcosServerGroup(String account, App app) {
    this.app = app
    this.json = app.toString()
    def id = DcosSpinnakerId.parse(app.id, account)
    this.name = id.name
    this.region = id.safeRegion
    this.account = id.account
    this.kind = "Application"

    populateLoadBalancers(app)

    this.cpus = app.cpus
    this.mem = app.mem
    this.disk = app.disk
    this.labels = app.labels

    this.createdTime = app.versionInfo?.lastConfigChangeAt ? Instant.parse(app.versionInfo.lastConfigChangeAt).toEpochMilli() : null

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
      def lbPath = it.replace('_', '/')
      DcosSpinnakerId.validate(lbPath, account) ? DcosSpinnakerId.parse(lbPath, account) : null
    })?.toSet() ?: []

    loadBalancers = fullyQualifiedLoadBalancers?.collect { it.name } ?: []
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

    def imageDesc = buildImageDescription(app.container?.docker?.image)

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

  static ImageDescription buildImageDescription(String image) {

    if (!image || image.isEmpty()) {
      return null
    }

    def sIndex = image.indexOf('/')
    def result = new ImageDescription()

    // No slash means we only provided a repository name & optional tag.
    if (sIndex < 0) {
      result.repository = image
    } else {
      def sPrefix = image.substring(0, sIndex)

      // Check if the content before the slash is a registry (either localhost, or a URL)
      if (sPrefix.startsWith('localhost') || sPrefix.contains('.')) {
        result.registry = sPrefix

        image = image.substring(sIndex + 1)
      }
    }

    def cIndex = image.indexOf(':')

    if (cIndex < 0) {
      result.repository = image
    } else {
      result.tag = image.substring(cIndex + 1)
      result.repository = image.subSequence(0, cIndex)
    }

    normalizeImageDescription(result)
    result
  }

  static Void normalizeImageDescription(ImageDescription image) {
    if (!image.registry) {
      image.registry = "hub.docker.com" // TODO configure or pull from docker registry account
    }

    if (!image.tag) {
      image.tag = "latest"
    }

    if (!image.repository) {
      throw new IllegalArgumentException("Image descriptions must provide a repository.")
    }
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    imagesSummary?.summaries?.get(0)
  }

  static Set filterInstancesByHealthState(Set instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }
}
