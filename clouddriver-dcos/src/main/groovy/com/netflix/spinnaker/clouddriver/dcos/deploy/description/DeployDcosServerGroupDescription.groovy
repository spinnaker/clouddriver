package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.Canonical

class DeployDcosServerGroupDescription extends AbstractDcosCredentialsDescription implements DeployDescription {
  String application
  String stack
  String detail
  String region
  String cmd
  List<String> args = new ArrayList<>()
  String user
  Map<String, Object> env = new HashMap<>()
  Integer instances
  Double cpus
  Double mem
  Double disk
  Double gpus
  List<List<String>> constraints = new ArrayList<>()
  List<String> fetch = new ArrayList<>()
  List<String> storeUrls = new ArrayList<>()
  Integer backoffSeconds
  Double backoffFactor
  Integer maxLaunchDelaySeconds
  Container container
  List<HealthCheck> healthChecks = new ArrayList<>()
  List<Object> readinessChecks = new ArrayList<>()
  List<String> dependencies = new ArrayList<>()
  UpgradeStrategy upgradeStrategy
  Map<String, String> labels = new HashMap<>()
  List<String> acceptedResourceRoles = null
  String ipAddress
  String version
  String residency
  Integer taskKillGracePeriodSeconds
  Map<String, Object> secrets = new HashMap<>()
  List<Integer> ports = new ArrayList<>()
  List<PortDefinition> portDefinitions = new ArrayList<>()
  Boolean requirePorts

  @Canonical
  static class Container {
    String type
    List<Volume> volumes = new ArrayList<>()
    Docker docker
  }

  @Canonical
  static class Docker {
      String image
      String network
      List<PortMapping> portMappings = new ArrayList<>()
      Boolean privileged
      List<Parameter> parameters = new ArrayList<>()
      Boolean forcePullImage
  }

  @Canonical
  static class PortMapping {
    Integer containerPort
    Integer hostPort
    Integer servicePort
    String protocol
    Map<String, String> labels = new HashMap<>()
  }

  @Canonical
  static class Parameter {
    String key
    String value
  }

  @Canonical
  static class Volume {
    String containerPath
    String hostPath
    String mode
  }

  @Canonical
  static class PortDefinition {
    Integer port
    String protocol
    Map<String, String> labels = new HashMap<>()
  }

  @Canonical
  static class UpgradeStrategy {
    Double minimumHealthCapacity
    Double maximumOverCapacity
  }

  @Canonical
  static class HealthCheck {
    String path
    String protocol
    String command
    Integer portIndex
    Integer gracePeriodSeconds
    Integer intervalSeconds
    Integer timeoutSeconds
    Integer maxConsecutiveFailures
    Boolean ignoreHttp1xx
  }
}
