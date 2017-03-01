package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
class DeployDcosServerGroupDescription extends AbstractDcosCredentialsDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  String region
  String cmd
  List<String> args = new ArrayList<>()
  String dcosUser
  Map<String, Object> env = new HashMap<>()
  Integer desiredCapacity
  Double cpus
  Double mem
  Double disk
  Double gpus
  String constraints
  List<Fetchable> fetch = new ArrayList<>()
  List<String> storeUrls = new ArrayList<>()
  Integer backoffSeconds
  Double backoffFactor
  Integer maxLaunchDelaySeconds
  Docker docker
  List<HealthCheck> healthChecks = new ArrayList<>()
  List<ReadinessCheck> readinessChecks = new ArrayList<>()
  List<String> dependencies = new ArrayList<>()
  UpgradeStrategy upgradeStrategy
  Map<String, String> labels = new HashMap<>()
  List<String> acceptedResourceRoles = null
  String version
  Residency residency
  Integer taskKillGracePeriodSeconds
  Map<String, Object> secrets = new HashMap<>()

  // TODO don't think this should be here - In dcos, only defined at the "docker" level
  String networkType
  String networkName
  List<ServiceEndpoint> serviceEndpoints = new ArrayList<>()
  List<PersistentVolume> persistentVolumes = new ArrayList<>()
  List<DockerVolume> dockerVolumes = new ArrayList<>()
  List<ExternalVolume> externalVolumes = new ArrayList<>()
  Boolean requirePorts

  @Canonical
  static class Container {
    String type
  }

  // TODO don't think we need a lot of this stuff (fromContext, fromTrigger, cluster, account)
  @Canonical
  static class Image {
    String registry
    String repository
    String tag
    String imageId
    String stageId
    String cluster
    String account
    String pattern
    String fromContext
    String fromTrigger
  }

  @Canonical
  static class Fetchable {
    String uri
    Boolean executable
    Boolean extract
    Boolean cache
    String outputFile
  }

  @Canonical
  static class Docker {
    Image image

    // TODO this isn't actually used
    String network
    boolean privileged
    Map<String, String> parameters
    boolean forcePullImage
  }

  @Canonical
  static class UpgradeStrategy {
    Double minimumHealthCapacity
    Double maximumOverCapacity
  }

  @Canonical
  static class HealthCheck {
    String protocol
    String path
    String command
    Integer port
    Integer portIndex
    Integer gracePeriodSeconds
    Integer intervalSeconds
    Integer timeoutSeconds
    Integer maxConsecutiveFailures
    boolean ignoreHttp1xx
  }

  @Canonical
  static class ServiceEndpoint {
    // TODO don't think this should be here - In dcos, only defined at the "docker" level
    String networkType
    Integer port
    String name
    String protocol
    boolean loadBalanced
    boolean exposeToHost
    Map<String, String> labels = new HashMap<>()
  }

  @Canonical
  static class Volume {
    String containerPath
    String mode
  }

  @Canonical
  static class PersistentVolume extends Volume {
    PersistentStorage persistent
  }

  @Canonical
  static class DockerVolume extends Volume {
    String hostPath
  }

  @Canonical
  static class ExternalVolume extends Volume {
    ExternalStorage external
  }

  @Canonical
  static class PersistentStorage {
    Integer size
  }

  @Canonical
  static class ExternalStorage {
    String name
    String provider
    Map<String, String> options
    String mode
  }

  @Canonical
  static class Residency {
    String taskLostBehaviour
    Integer relaunchEscalationTimeoutSeconds
  }

  @Canonical
  static class ReadinessCheck {
    String name
    String protocol
    String path
    String portName
    Integer intervalSeconds
    Integer timeoutSeconds
    Collection<Integer> httpStatusCodesForReady
    boolean preserveLastResponse
  }
}
