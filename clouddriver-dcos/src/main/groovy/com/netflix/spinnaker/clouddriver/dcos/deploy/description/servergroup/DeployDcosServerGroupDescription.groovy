package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.Canonical

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
  List<String> fetch = new ArrayList<>()
  List<String> storeUrls = new ArrayList<>()
  Integer backoffSeconds
  Double backoffFactor
  Integer maxLaunchDelaySeconds
  Docker docker
  List<HealthCheck> healthChecks = new ArrayList<>()
  List<Object> readinessChecks = new ArrayList<>()
  List<String> dependencies = new ArrayList<>()
  UpgradeStrategy upgradeStrategy
  Map<String, String> labels = new HashMap<>()
  List<String> acceptedResourceRoles = null
  String version
  String residency
  Integer taskKillGracePeriodSeconds
  Map<String, Object> secrets = new HashMap<>()
  NetworkType networkType
  List<ServiceEndpoint> serviceEndpoints = new ArrayList<>()
  List<PersistentVolume> persistentVolumes = new ArrayList<>()
  List<DockerVolume> dockerVolumes = new ArrayList<>()
  List<ExternalVolume> externalVolumes = new ArrayList<>()
  Boolean requirePorts

  @Canonical
  static class Container {
    String type
  }

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
  static class Docker {
    Image image
    NetworkType network
    boolean privileged
    List<Parameter> parameters = new ArrayList<>()
    boolean forcePullImage
  }

  @Canonical
  static class Parameter {
    String key
    String value
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
  static class NetworkType {
    String type
    String name
  }

  @Canonical
  static class ServiceEndpoint {
    NetworkType networkType
    Integer port
    String name
    String protocol
    boolean isLoadBalanced
    boolean exposeToHost
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
}
