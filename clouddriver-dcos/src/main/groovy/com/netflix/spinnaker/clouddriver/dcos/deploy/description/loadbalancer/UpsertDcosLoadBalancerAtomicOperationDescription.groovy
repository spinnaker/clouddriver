package com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import groovy.transform.Canonical
import mesosphere.marathon.client.model.v2.PortDefinition

class UpsertDcosLoadBalancerAtomicOperationDescription extends AbstractDcosCredentialsDescription {

  String name
  String app
  String stack
  String detail
  boolean bindHttpHttps
  double cpus
  int instances
  double mem
  List<String> acceptedResourceRoles

  PortRange portRange

  static class PortRange {
    String protocol
    int minPort
    int maxPort
  }
}
