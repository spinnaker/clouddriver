package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.Canonical
import mesosphere.marathon.client.model.v2.App

import java.time.Instant

/**
 * Equivalent of a Dcos {@link mesosphere.marathon.client.model.v2.App}
 */
@Canonical
class DcosServerGroup implements ServerGroup, Serializable {

  // TODO don't store app?
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
  Set<DcosInstance> instances = [] as Set

  DcosServerGroup() {
  }

  DcosServerGroup(String name, String group, String account) {
    this.name = name
    this.group = group
    this.region = group ?: "root"
    this.account = account
  } //default constructor for deserialization

  DcosServerGroup(App app) {
    this.app = app
    this.json = app.toString()
    def id = DcosSpinnakerId.parse(app.id)
    this.name = id.name
    this.group = id.group
    this.region = (id.group ?: "root").replace("/", "_")
    this.account = id.account
    this.kind = "Application"
    this.loadBalancers = getLoadBalancers(app)

    this.cpus = app.cpus
    this.mem = app.mem
    this.disk = app.disk
    this.labels = app.labels

    // TODO WHAT IS HAPPENING. WHY THE FUCK IS THIS GETTING SET TO NULL. AM I AN IDIOT. IT NEVER RETURNS NULL.
    this.createdTime = Instant.parse(app.versionInfo?.lastConfigChangeAt).toEpochMilli()

    // TODO remove when I have instance caching? this seems better anyways
    this.instances = app.tasks.collect({ new DcosInstance(it) }) as Set
  }

  static Set<String> getLoadBalancers(App app) {

    // TODO Going to be problematic!
    app.labels.findResults { key, val ->
      // TODO pull this regex out.
      def regexStr = /^HAPROXY_(\d*_)*GROUP$/

      if (key.matches(regexStr)) {
        return val.split(",")
      } else {
        return null
      }
    }.flatten().collect({it.split("_")[1]}).toSet()
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

  Map<String, Object> getBuildInfo() {
    def buildInfo = [:]

    // TODO handle when returned image description is null.
    def imageDesc = buildImageDescription(app.container?.docker?.image)

    buildInfo.imageDesc = imageDesc
    buildInfo.images = ["$imageDesc.repository:$imageDesc.tag".toString()]

    //def parsedName = Names.parseName(name)
    //buildInfo.createdBy = this.deployDescription?.deployment?.enabled ? parsedName.cluster : null

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
          // TODO we don't have too much info here.
          String serverGroupName = name
          String imageName = "${parts[0]}-${parts[1]}".toString()
          String imageId = app.container?.docker?.image

          @Override
          Map<String, Object> getBuildInfo() {
            bi
          }

          @Override
          Map<String, Object> getImage() {
            // TODO fill out
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
      image.registry = "dockerhub.cerner.com" // TODO configure or pull from docker registry account?
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
