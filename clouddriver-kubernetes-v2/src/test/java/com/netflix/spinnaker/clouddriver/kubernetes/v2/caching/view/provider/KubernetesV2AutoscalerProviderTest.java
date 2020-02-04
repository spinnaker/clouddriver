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
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.INSTANCES;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Autoscaler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesV2AutoscalerProviderTest {

  private KubernetesCacheUtils cacheUtils;
  private KubernetesSpinnakerKindMap kindMap;
  private KubernetesV2AutoscalerProvider provider;

  private static final String APPLICATION = "test-app";
  private static final String HPA_KEY =
      Keys.InfrastructureCacheKey.createKey(
          KubernetesKind.HORIZONTAL_POD_AUTOSCALER, "my-account", "default", "hpa-1");

  @BeforeEach
  public void setup() {
    cacheUtils = mock(KubernetesCacheUtils.class);
    kindMap = mock(KubernetesSpinnakerKindMap.class);
    provider = new KubernetesV2AutoscalerProvider(cacheUtils, kindMap);
    when(kindMap.translateSpinnakerKind(AUTOSCALERS))
        .thenReturn(ImmutableSet.of(KubernetesKind.HORIZONTAL_POD_AUTOSCALER));
    when(kindMap.translateSpinnakerKind(SERVER_GROUPS))
        .thenReturn(ImmutableSet.of(KubernetesKind.REPLICA_SET));
    when(kindMap.translateSpinnakerKind(INSTANCES)).thenReturn(ImmutableSet.of(KubernetesKind.POD));
  }

  @Test
  void getAutoscalersByApplication() {
    CacheData autoscalerCacheData = mock(CacheData.class);
    CacheData serverGroupCacheData = mock(CacheData.class);
    CacheData instanceCacheData = mock(CacheData.class);

    Collection<CacheData> autoscalerCaches = Collections.singleton(autoscalerCacheData);

    Collection<CacheData> serverGroupCaches = Collections.singleton(serverGroupCacheData);

    Collection<CacheData> instanceCaches = Collections.singleton(instanceCacheData);

    when(cacheUtils.getTransitiveRelationship(
            APPLICATIONS.toString(),
            Collections.singletonList(Keys.ApplicationCacheKey.createKey(APPLICATION)),
            KubernetesKind.HORIZONTAL_POD_AUTOSCALER.toString()))
        .thenReturn(autoscalerCaches);

    when(cacheUtils.loadRelationshipsFromCache(
            autoscalerCaches, KubernetesKind.REPLICA_SET.toString()))
        .thenReturn(serverGroupCaches);

    when(cacheUtils.loadRelationshipsFromCache(serverGroupCaches, KubernetesKind.POD.toString()))
        .thenReturn(instanceCaches);

    when(cacheUtils.mapByRelationship(serverGroupCaches, AUTOSCALERS)).thenReturn(new HashMap<>());
    when(cacheUtils.mapByRelationship(instanceCaches, SERVER_GROUPS)).thenReturn(new HashMap<>());

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("manifest", mock(KubernetesManifest.class));

    when(autoscalerCacheData.getId()).thenReturn(HPA_KEY);
    when(autoscalerCacheData.getAttributes()).thenReturn(attributes);

    Set<KubernetesV2Autoscaler> autoscalers = provider.getAutoscalersByApplication(APPLICATION);

    assertThat(autoscalers.size()).isEqualTo(1);
  }

  @Test
  void getEmptyAutoscalers() {
    Collection<CacheData> autoscalerCaches = Collections.emptyList();

    Collection<CacheData> serverGroupCaches = Collections.emptyList();

    Collection<CacheData> instanceCaches = Collections.emptyList();

    when(cacheUtils.getTransitiveRelationship(
            APPLICATIONS.toString(),
            Collections.singletonList(Keys.ApplicationCacheKey.createKey(APPLICATION)),
            KubernetesKind.HORIZONTAL_POD_AUTOSCALER.toString()))
        .thenReturn(autoscalerCaches);

    when(cacheUtils.loadRelationshipsFromCache(
            autoscalerCaches, KubernetesKind.REPLICA_SET.toString()))
        .thenReturn(serverGroupCaches);

    when(cacheUtils.loadRelationshipsFromCache(serverGroupCaches, KubernetesKind.POD.toString()))
        .thenReturn(instanceCaches);

    when(cacheUtils.mapByRelationship(serverGroupCaches, AUTOSCALERS)).thenReturn(new HashMap<>());
    when(cacheUtils.mapByRelationship(instanceCaches, SERVER_GROUPS)).thenReturn(new HashMap<>());

    Set<KubernetesV2Autoscaler> autoscalers = provider.getAutoscalersByApplication(APPLICATION);

    verify(cacheUtils)
        .getTransitiveRelationship(
            APPLICATIONS.toString(),
            Collections.singletonList(Keys.ApplicationCacheKey.createKey(APPLICATION)),
            KubernetesKind.HORIZONTAL_POD_AUTOSCALER.toString());

    assertThat(autoscalers.size()).isEqualTo(0);
  }
}
