/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.model.Cluster;
import java.io.Serializable;
import java.util.Set;

public class AliCloudCluster implements Cluster, Serializable {

  private String name;
  private String type;
  private String accountName;
  private Set<AliCloudServerGroup> serverGroups;
  private Set<AliCloudLoadBalancer> loadBalancers;

  public AliCloudCluster(
      String name,
      String type,
      String accountName,
      Set<AliCloudServerGroup> serverGroups,
      Set<AliCloudLoadBalancer> loadBalancers) {
    this.name = name;
    this.type = type;
    this.accountName = accountName;
    this.serverGroups = serverGroups;
    this.loadBalancers = loadBalancers;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public Set<AliCloudServerGroup> getServerGroups() {
    return serverGroups;
  }

  @Override
  public Set<AliCloudLoadBalancer> getLoadBalancers() {
    return loadBalancers;
  }
}
