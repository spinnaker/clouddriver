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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.AUTOSCALERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATIONS;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Autoscaler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.model.AutoscalerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2AutoscalerProvider implements AutoscalerProvider<KubernetesV2Autoscaler> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesV2AutoscalerProvider(
      KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
  }

  @Override
  public Set<KubernetesV2Autoscaler> getAutoscalersByApplication(String application) {
    List<CacheData> autoscalerData =
        kindMap.translateSpinnakerKind(AUTOSCALERS).stream()
            .map(
                kind ->
                    cacheUtils.getTransitiveRelationship(
                        APPLICATIONS.toString(),
                        Collections.singletonList(Keys.ApplicationCacheKey.createKey(application)),
                        kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    return fromAutoscalerCacheData(autoscalerData);
  }

  private Set<KubernetesV2Autoscaler> fromAutoscalerCacheData(List<CacheData> autoscalerData) {
    List<CacheData> serverGroupData =
        kindMap.translateSpinnakerKind(SERVER_GROUPS).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(autoscalerData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    Map<String, List<CacheData>> autoscalerToServerGroups =
        cacheUtils.mapByRelationship(serverGroupData, AUTOSCALERS);

    return autoscalerData.stream()
        .map(
            cd ->
                KubernetesV2Autoscaler.fromCacheData(
                    cd, autoscalerToServerGroups.getOrDefault(cd.getId(), new ArrayList<>())))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }
}
