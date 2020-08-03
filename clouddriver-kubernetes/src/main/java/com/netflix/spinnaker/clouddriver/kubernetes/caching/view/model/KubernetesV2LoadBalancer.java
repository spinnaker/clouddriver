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

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public final class KubernetesV2LoadBalancer
    implements KubernetesResource, LoadBalancer, LoadBalancerProvider.Details {
  private final Set<LoadBalancerServerGroup> serverGroups;
  private final KubernetesManifest manifest;
  private final String account;

  private KubernetesV2LoadBalancer(
      KubernetesManifest manifest, String key, Set<LoadBalancerServerGroup> serverGroups) {
    this.manifest = manifest;
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.serverGroups = serverGroups;
  }

  @Nullable
  @ParametersAreNonnullByDefault
  public static KubernetesV2LoadBalancer fromCacheData(
      CacheData cd, Set<LoadBalancerServerGroup> loadBalancerServerGroups) {
    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);
    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }
    return new KubernetesV2LoadBalancer(manifest, cd.getId(), loadBalancerServerGroups);
  }

  @Override
  public String getType() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public String getName() {
    return getManifest().getFullResourceName();
  }

  @Override
  public String getDisplayName() {
    return getManifest().getName();
  }

  @Override
  public KubernetesApiVersion getApiVersion() {
    return getManifest().getApiVersion();
  }

  @Override
  public String getNamespace() {
    return getManifest().getNamespace();
  }

  public String getRegion() {
    return getManifest().getNamespace();
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public Map<String, String> getLabels() {
    return getManifest().getLabels();
  }

  @Override
  public KubernetesKind getKind() {
    return getManifest().getKind();
  }

  @Override
  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(account)
        .withResource(KubernetesManifest.class)
        .deriveMoniker(manifest);
  }
}
