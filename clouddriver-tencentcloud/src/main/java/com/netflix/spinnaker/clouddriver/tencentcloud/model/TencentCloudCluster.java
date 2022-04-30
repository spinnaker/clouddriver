/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class TencentCloudCluster implements Cluster {

  private String name;
  private String accountName;
  private Set<TencentCloudServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<>());
  private Set<TencentCloudLoadBalancer> loadBalancers =
      Collections.synchronizedSet(new HashSet<>());

  public final String getType() {
    return TencentCloudProvider.ID;
  }
}
