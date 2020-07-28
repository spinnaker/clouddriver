/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

@Value
public final class KubernetesV2Cluster implements Cluster {
  private final String name;
  private final Moniker moniker;
  private final String type = KubernetesCloudProvider.ID;
  private final String accountName;
  private final Set<KubernetesV2ServerGroup> serverGroups;
  private final Set<KubernetesV2LoadBalancer> loadBalancers;
  private final String application;

  public KubernetesV2Cluster(String rawKey) {
    this(rawKey, ImmutableList.of(), ImmutableList.of());
  }

  public KubernetesV2Cluster(
      String rawKey,
      List<KubernetesV2ServerGroup> serverGroups,
      List<KubernetesV2LoadBalancer> loadBalancers) {
    Keys.ClusterCacheKey key = (Keys.ClusterCacheKey) Keys.parseKey(rawKey).get();
    this.name = key.getName();
    this.accountName = key.getAccount();
    this.application = key.getApplication();
    this.moniker = Moniker.builder().cluster(name).app(application).build();
    this.serverGroups = new HashSet<>(serverGroups);
    this.loadBalancers = new HashSet<>(loadBalancers);
  }
}
