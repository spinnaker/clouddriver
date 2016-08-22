/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.collect.Sets
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor.HealthMonitorType
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import groovy.transform.Canonical
import org.openstack4j.model.network.ext.HealthMonitor as Openstack4jHealthMonitor
import org.openstack4j.model.network.ext.LbPool

@Canonical
@JsonIgnoreProperties(['portRegex','portPattern','createdRegex','createdPattern'])
class OpenstackLoadBalancer implements LoadBalancer, Serializable, LoadBalancerResolver {

  String type = OpenstackCloudProvider.ID
  String account
  String region
  String id
  String name
  String description
  String status
  String protocol
  String method
  String ip
  Integer externalPort
  String subnetId
  String subnetName
  String networkId
  String networkName
  Set<HealthMonitor> healthChecks
  Set<LoadBalancerServerGroup> serverGroups = Sets.newConcurrentHashSet()

  static OpenstackLoadBalancer from(LbPool pool, OpenstackVip vip, OpenstackSubnet subnet, OpenstackNetwork network, OpenstackFloatingIP ip,
                                    Set<HealthMonitor> healthMonitors, String account, String region) {
    if (!pool) {
      throw new IllegalArgumentException("Pool must not be null.")
    }
    new OpenstackLoadBalancer(account: account, region: region, id: pool.id, name: pool.name, description: pool.description,
      status: pool.status, protocol: pool.protocol?.name(), method: pool.lbMethod?.name(),
      ip: ip?.floatingIpAddress, externalPort: vip?.port, subnetId: subnet?.id, subnetName: subnet?.name,
      networkId: network?.id, networkName: network?.name,
      healthChecks: healthMonitors?.collect(this.&buildHealthMonitor)?.toSet())
  }

  Integer getInternalPort() {
    parseInternalPort(description)
  }

  Long getCreatedTime() {
    parseCreatedTime(description)
  }

  static HealthMonitor buildHealthMonitor(Openstack4jHealthMonitor monitor) {
    new HealthMonitor(id: monitor.id,
      type: HealthMonitorType.forValue(monitor.type.name()),
      delay : monitor.delay,
      timeout: monitor.timeout,
      maxRetries: monitor.maxRetries,
      httpMethod: monitor.httpMethod,
      url: monitor.urlPath,
      expectedCodes: monitor.expectedCodes?.split(',')?.toList()?.collect { Integer.parseInt(it) })
  }
}
