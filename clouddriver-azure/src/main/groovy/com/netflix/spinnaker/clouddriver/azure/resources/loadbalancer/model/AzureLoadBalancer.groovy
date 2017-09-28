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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.moniker.Moniker

class AzureLoadBalancer implements LoadBalancer {

  String account
  String name
  String region
  String vnet
  String subnet
  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  String cluster
  final String type = AzureCloudProvider.ID
  final String cloudProvider = AzureCloudProvider.ID

  void setMoniker(Moniker _ignored) {}

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  AzureLoadBalancer() {
  }

  @JsonAnyGetter
  Map<String,Object> any() {
    return dynamicProperties
  }

  @JsonAnySetter
  void set(String name, Object value) {
    dynamicProperties.put(name, value)
  }

  @Override
  boolean equals(Object o) {
    if (!o instanceof AzureLoadBalancer) {
      return false
    }
    AzureLoadBalancer a = (AzureLoadBalancer)o;
    a.getAccount() == this.getAccount() && a.getName() == this.getName() && a.getType() == this.getType() && a.region == this.region

    // TODO Implement logic to compare server groups and regions(?)
  }

  @Override
  int hashCode() {
    getAccount().hashCode() + getName().hashCode() + getType().hashCode() + region.hashCode();
    // TODO Implement logic to add hash code for server groups and regions(?)
  }
}
