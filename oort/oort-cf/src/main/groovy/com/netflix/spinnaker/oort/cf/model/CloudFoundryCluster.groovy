/*
 * Copyright 2015 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.LoadBalancer
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
/**
 * One part of a Cloud Foundry application (like blue or green).
 *
 *
 */
@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class CloudFoundryCluster implements Cluster, Serializable {

  String name
  String type = 'cf'
  String accountName
  Set<CloudFoundryServerGroup> serverGroups = [] as Set<CloudFoundryServerGroup>

  @Override
  Set<LoadBalancer> getLoadBalancers() {
    Collections.emptySet()
  }

}
