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

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Autoscaler;
import com.netflix.spinnaker.clouddriver.model.AutoscalerServerGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class KubernetesV2Autoscaler extends ManifestBasedModel implements Autoscaler {
  private KubernetesManifest manifest;
  private Keys.InfrastructureCacheKey key;
  private Set<AutoscalerServerGroup> serverGroups = new HashSet<>();

  private KubernetesV2Autoscaler(
      KubernetesManifest manifest, String key, Set<AutoscalerServerGroup> serverGroups) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.serverGroups = serverGroups;
  }

  public static KubernetesV2Autoscaler fromCacheData(
      CacheData cd,
      List<CacheData> serverGroupData,
      Map<String, List<CacheData>> serverGroupToInstanceData) {
    if (cd == null) {
      return null;
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    Set<AutoscalerServerGroup> serverGroups =
        serverGroupData.stream()
            .map(
                d ->
                    KubernetesV2ServerGroup.fromCacheData(
                        KubernetesV2ServerGroupCacheData.builder()
                            .serverGroupData(d)
                            .instanceData(serverGroupToInstanceData.get(d.getId()))
                            .loadBalancerData(new ArrayList<>())
                            .autoscalerData(new ArrayList<>())
                            .build()))
            .filter(Objects::nonNull)
            .map(KubernetesV2ServerGroup::toAutoscalerServerGroup)
            .collect(Collectors.toSet());

    return new KubernetesV2Autoscaler(manifest, cd.getId(), serverGroups);
  }
}
