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
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Value
public final class KubernetesV2LoadBalancer extends ManifestBasedModel
    implements LoadBalancer, LoadBalancerProvider.Details {
  private final Set<LoadBalancerServerGroup> serverGroups;
  private final KubernetesManifest manifest;
  private final Keys.InfrastructureCacheKey key;

  private KubernetesV2LoadBalancer(
      KubernetesManifest manifest, String key, Set<LoadBalancerServerGroup> serverGroups) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.serverGroups = serverGroups;
  }

  public static KubernetesV2LoadBalancer fromCacheData(
      CacheData cd,
      List<CacheData> serverGroupData,
      Map<String, ? extends Collection<CacheData>> serverGroupToInstanceData) {
    if (cd == null) {
      return null;
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    Set<LoadBalancerServerGroup> serverGroups =
        serverGroupData.stream()
            .map(
                d ->
                    KubernetesV2ServerGroup.fromCacheData(
                        KubernetesV2ServerGroupCacheData.builder()
                            .serverGroupData(d)
                            .instanceData(serverGroupToInstanceData.get(d.getId()))
                            .loadBalancerKeys(ImmutableList.of(cd.getId()))
                            .build()))
            .filter(Objects::nonNull)
            .map(KubernetesV2ServerGroup::toLoadBalancerServerGroup)
            .collect(Collectors.toSet());

    return new KubernetesV2LoadBalancer(manifest, cd.getId(), serverGroups);
  }
}
