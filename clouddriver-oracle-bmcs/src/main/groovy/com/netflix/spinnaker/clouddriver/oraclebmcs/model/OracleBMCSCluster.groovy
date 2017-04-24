/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class OracleBMCSCluster {

  String name
  String accountName
  Set<OracleBMCSServerGroup> serverGroups

  @JsonIgnore
  View getView() {
    new View()
  }

  @Canonical
  class View implements Cluster {

    final String type = OracleBMCSCloudProvider.ID

    String name = OracleBMCSCluster.this.name
    String accountName = OracleBMCSCluster.this.accountName
    Set<OracleBMCSServerGroup.View> serverGroups = OracleBMCSCluster.this.serverGroups.collect { it.getView() } as Set
    Set<LoadBalancer> loadBalancers = [] as Set
  }
}
