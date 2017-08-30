package com.netflix.spinnaker.clouddriver.ecs.model;

import com.netflix.spinnaker.clouddriver.model.Application;

import java.util.Map;
import java.util.Set;

public class EcsApplication implements Application {

  private String name;
  Map<String, String> attributes;
  Map<String, Set<String>> clusterNames;

  public EcsApplication() {
  }

  public EcsApplication(String name, Map<String, String> attributes, Map<String, Set<String>> clusterNames) {
    this.name = name;
    this.attributes = attributes;
    this.clusterNames = clusterNames;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }

  @Override
  public Map<String, Set<String>> getClusterNames() {
    return clusterNames;
  }
}
