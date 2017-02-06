package com.netflix.spinnaker.clouddriver.dcos.model

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.clouddriver.model.Application
import groovy.transform.Canonical

class DcosApplication implements Application, Serializable {
  public static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<Map<String, String>>() {}

  final String name
  final Map<String, String> attributes
  final Map<String, Set<String>> clusterNames

  DcosApplication(String name, Map<String, String> attributes, Map<String, Set<String>> clusterNames) {
    this.name = name
    this.attributes = attributes
    this.clusterNames = clusterNames
  }
}