/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Autoscaler;
import com.netflix.spinnaker.clouddriver.model.ServerGroupSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class KubernetesV2Autoscaler extends ManifestBasedModel implements Autoscaler {
  private final KubernetesManifest manifest;
  private final Keys.InfrastructureCacheKey key;
  private final ImmutableSet<ServerGroupSummary> serverGroupSummaries;

  private KubernetesV2Autoscaler(
      KubernetesManifest manifest, String key, Iterable<ServerGroupSummary> serverGroupSummaries) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.serverGroupSummaries = ImmutableSet.copyOf(serverGroupSummaries);
  }

  public static Optional<KubernetesV2Autoscaler> fromCacheData(
      CacheData cd, List<CacheData> serverGroupData) {
    if (cd == null) {
      return Optional.empty();
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return Optional.empty();
    }

    Set<ServerGroupSummary> serverGroupSummaries =
        serverGroupData.stream()
            .map(
                d ->
                    KubernetesV2ServerGroup.fromCacheData(
                        KubernetesV2ServerGroupCacheData.builder()
                            .serverGroupData(d)
                            .instanceData(new ArrayList<>())
                            .loadBalancerData(new ArrayList<>())
                            .autoscalerData(new ArrayList<>())
                            .build()))
            .filter(Objects::nonNull)
            .map(KubernetesV2ServerGroup::toServerGroupSummary)
            .collect(Collectors.toSet());

    return Optional.of(new KubernetesV2Autoscaler(manifest, cd.getId(), serverGroupSummaries));
  }
}
