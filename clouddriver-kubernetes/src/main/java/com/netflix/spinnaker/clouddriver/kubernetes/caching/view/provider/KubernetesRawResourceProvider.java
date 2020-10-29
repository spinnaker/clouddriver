/*
 * Copyright 2020 Coveo, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ApplicationCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesRawResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.RawResourcesEndpointConfig;
import com.netflix.spinnaker.clouddriver.model.RawResourceProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesRawResourceProvider implements RawResourceProvider {
  private final KubernetesCacheUtils cacheUtils;
  private final RawResourcesEndpointConfig configuration;

  @Autowired
  KubernetesRawResourceProvider(
      KubernetesCacheUtils cacheUtils, KubernetesConfigurationProperties globalConfig) {
    this.cacheUtils = cacheUtils;
    this.configuration = globalConfig.getRawResourcesEndpointConfig();
    this.configuration.validate();
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public Set<KubernetesRawResource> getApplicationRawResources(String application) {
    return cacheUtils
        .getSingleEntry(APPLICATIONS.toString(), ApplicationCacheKey.createKey(application))
        .map(
            applicationData ->
                fromRawResourceCacheData(cacheUtils.getAllRelationships(applicationData)))
        .orElseGet(ImmutableSet::of);
  }

  private Set<KubernetesRawResource> fromRawResourceCacheData(
      Collection<CacheData> rawResourceData) {
    List<String> kinds = configuration.getKinds();
    List<String> omitKinds = configuration.getOmitKinds();
    return rawResourceData.stream()
        .map(KubernetesRawResource::fromCacheData)
        .filter(Objects::nonNull)
        .filter(
            resource ->
                (kinds.isEmpty() || kinds.contains(resource.getKind().toString()))
                    && !omitKinds.contains(resource.getKind().toString()))
        .collect(Collectors.toSet());
  }
}
