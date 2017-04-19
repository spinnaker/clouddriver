package com.netflix.spinnaker.clouddriver.dcos.deploy.description.job

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
class RunDcosJobDescription extends AbstractDcosCredentialsDescription implements DeployDescription {
  GeneralSettings general
  Schedule schedule
  Docker docker
  Map<String, String> labels
  Map<String, String> env
  String user
  Integer maxLaunchDelay
  List<Constraint> constraints
  RestartPolicy restartPolicy
  List<Artifact> artifacts
  List<Volume> volumes

  @Canonical
  static class GeneralSettings {
    String id
    String description
    Double cpus
    Double gpus
    Integer mem
    Integer disk
    String cmd
  }

  @Canonical
  static class Docker {
    Image image
  }

  @Canonical
  static class Image {
    String registry
    String repository
    String tag
  }

  @Canonical
  static class Schedule {
    String id
    Boolean enabled
    String cron
    String timezone
    Integer startingDeadlineSeconds
  }

  @Canonical
  static class Constraint {
    String attribute
    String operator
    String value
  }

  @Canonical
  static class RestartPolicy {
    Integer activeDeadlineSeconds
    String policy
  }

  @Canonical
  static class Artifact {
    String uri
    Boolean executable
    Boolean extract
    Boolean cache
  }

  @Canonical
  static class Volume {
    String containerPath
    String hostPath
    String mode
  }
}
