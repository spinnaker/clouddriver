/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeSuper=true)
@EqualsAndHashCode(callSuper=true)
class GoogleInternalHttpLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.INTERNAL_MANAGED
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.INTERNAL_MANAGED

  /**
   * Default backend service a request is sent to if no host rules are matched.
   */
  GoogleBackendService defaultService

  /**
   * List of host rules that map incoming requests to GooglePathMatchers based on host header.
   */
  List<GoogleHostRule> hostRules

  /**
   * SSL certificate. This is populated only if this load balancer is a HTTPS load balancer.
   */
  String certificate

  /**
   * The name of the UrlMap this load balancer uses to route traffic. In the Google
   * Cloud Console, the L7 load balancer name is the same as this name.
   */
  String urlMapName

  String network
  String subnet

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View()
  }

  @Canonical
  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleInternalHttpLoadBalancer.this.type
    GoogleLoadBalancingScheme loadBalancingScheme = GoogleInternalHttpLoadBalancer.this.loadBalancingScheme

    String name = GoogleInternalHttpLoadBalancer.this.name
    String account = GoogleInternalHttpLoadBalancer.this.account
    String region = GoogleInternalHttpLoadBalancer.this.region
    Long createdTime = GoogleInternalHttpLoadBalancer.this.createdTime
    String ipAddress = GoogleInternalHttpLoadBalancer.this.ipAddress
    String ipProtocol = GoogleInternalHttpLoadBalancer.this.ipProtocol
    String portRange = GoogleInternalHttpLoadBalancer.this.portRange

    GoogleBackendService defaultService = GoogleInternalHttpLoadBalancer.this.defaultService
    List<GoogleHostRule> hostRules = GoogleInternalHttpLoadBalancer.this.hostRules
    String certificate = GoogleInternalHttpLoadBalancer.this.certificate
    String urlMapName = GoogleInternalHttpLoadBalancer.this.urlMapName
    String network = GoogleInternalHttpLoadBalancer.this.network
    String subnet = GoogleInternalHttpLoadBalancer.this.subnet

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
