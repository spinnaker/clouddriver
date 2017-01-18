package com.netflix.spinnaker.clouddriver.dcos.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.Canonical

class DeployDcosServerGroupDescription extends AbstractDcosCredentialsDescription implements DeployDescription {
  List<String> softConstraints
  List<String> hardConstraints
  String application
  String imageId
  Capacity capacity = new Capacity()
  Resources resources = new Resources()
  Map env
  Map labels
  String entryPoint

  @Canonical
  static class Capacity {
    int min
    int max
    int desired
  }

  @Canonical
  static class Resources {
    int cpu
    int memory
    int disk
    int gpu
    int networkMbps
    int[] ports
  }
}
