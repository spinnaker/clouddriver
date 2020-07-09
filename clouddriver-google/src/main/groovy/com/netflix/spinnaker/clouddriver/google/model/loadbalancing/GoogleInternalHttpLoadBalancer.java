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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import groovy.transform.ToString;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.EqualsAndHashCode;

@ToString(includeSuper = true)
@groovy.transform.EqualsAndHashCode(callSuper = true)
public class GoogleInternalHttpLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.INTERNAL_MANAGED;
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.INTERNAL_MANAGED;

  /** Default backend service a request is sent to if no host rules are matched. */
  GoogleBackendService defaultService;

  /** List of host rules that map incoming requests to GooglePathMatchers based on host header. */
  List<GoogleHostRule> hostRules;

  /** SSL certificate. This is populated only if this load balancer is a HTTPS load balancer. */
  String certificate;

  /**
   * The name of the UrlMap this load balancer uses to route traffic. In the Google Cloud Console,
   * the L7 load balancer name is the same as this name.
   */
  String urlMapName;

  String network;
  String subnet;

  @Override
  public GoogleLoadBalancerType getType() {
    return type;
  }

  @Override
  public void setType(GoogleLoadBalancerType type) {
    this.type = type;
  }

  @Override
  public GoogleLoadBalancingScheme getLoadBalancingScheme() {
    return loadBalancingScheme;
  }

  @Override
  public void setLoadBalancingScheme(GoogleLoadBalancingScheme loadBalancingScheme) {
    this.loadBalancingScheme = loadBalancingScheme;
  }

  public GoogleBackendService getDefaultService() {
    return defaultService;
  }

  public void setDefaultService(GoogleBackendService defaultService) {
    this.defaultService = defaultService;
  }

  public List<GoogleHostRule> getHostRules() {
    return hostRules;
  }

  public void setHostRules(List<GoogleHostRule> hostRules) {
    this.hostRules = hostRules;
  }

  public String getCertificate() {
    return certificate;
  }

  public void setCertificate(String certificate) {
    this.certificate = certificate;
  }

  public String getUrlMapName() {
    return urlMapName;
  }

  public void setUrlMapName(String urlMapName) {
    this.urlMapName = urlMapName;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  public String getSubnet() {
    return subnet;
  }

  public void setSubnet(String subnet) {
    this.subnet = subnet;
  }

  @JsonIgnore
  public InternalHttpLbView getView() {
    return new InternalHttpLbView();
  }

  @EqualsAndHashCode(callSuper = false)
  public class InternalHttpLbView extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleInternalHttpLoadBalancer.this.type;
    GoogleLoadBalancingScheme loadBalancingScheme =
        GoogleInternalHttpLoadBalancer.this.loadBalancingScheme;

    String name = GoogleInternalHttpLoadBalancer.this.getName();
    String account = GoogleInternalHttpLoadBalancer.this.getAccount();
    String region = GoogleInternalHttpLoadBalancer.this.getRegion();
    Long createdTime = GoogleInternalHttpLoadBalancer.this.getCreatedTime();
    String ipAddress = GoogleInternalHttpLoadBalancer.this.getIpAddress();
    String ipProtocol = GoogleInternalHttpLoadBalancer.this.getIpProtocol();
    String portRange = GoogleInternalHttpLoadBalancer.this.getPortRange();

    GoogleBackendService defaultService = GoogleInternalHttpLoadBalancer.this.defaultService;
    List<GoogleHostRule> hostRules = GoogleInternalHttpLoadBalancer.this.hostRules;
    String certificate = GoogleInternalHttpLoadBalancer.this.certificate;
    String urlMapName = GoogleInternalHttpLoadBalancer.this.urlMapName;
    String network = GoogleInternalHttpLoadBalancer.this.network;
    String subnet = GoogleInternalHttpLoadBalancer.this.subnet;

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();

    @Override
    public GoogleLoadBalancerType getLoadBalancerType() {
      return loadBalancerType;
    }

    @Override
    public GoogleLoadBalancingScheme getLoadBalancingScheme() {
      return loadBalancingScheme;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getAccount() {
      return account;
    }

    @Override
    public String getRegion() {
      return region;
    }

    @Override
    public Long getCreatedTime() {
      return createdTime;
    }

    @Override
    public String getIpAddress() {
      return ipAddress;
    }

    @Override
    public String getIpProtocol() {
      return ipProtocol;
    }

    @Override
    public String getPortRange() {
      return portRange;
    }

    public GoogleBackendService getDefaultService() {
      return defaultService;
    }

    public List<GoogleHostRule> getHostRules() {
      return hostRules;
    }

    public String getCertificate() {
      return certificate;
    }

    public String getUrlMapName() {
      return urlMapName;
    }

    public String getNetwork() {
      return network;
    }

    public String getSubnet() {
      return subnet;
    }

    @Override
    public Set<LoadBalancerServerGroup> getServerGroups() {
      return serverGroups;
    }
  }
}
