/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.model.ManifestProvider;
import com.netflix.spinnaker.moniker.Moniker;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class KubernetesV2ManifestProvider implements ManifestProvider<KubernetesV2Manifest> {
  private final KubernetesResourcePropertyRegistry registry;
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  public KubernetesV2ManifestProvider(KubernetesResourcePropertyRegistry registry, KubernetesCacheUtils cacheUtils) {
    this.registry = registry;
    this.cacheUtils = cacheUtils;
  }

  @Override
  public KubernetesV2Manifest getManifest(String account, String location, String name) {
    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (Exception e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    String key = Keys.infrastructure(
        kind,
        account,
        location,
        parsedName.getRight()
    );

    Optional<CacheData> dataOptional = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!dataOptional.isPresent()) {
      return null;
    }

    CacheData data = dataOptional.get();
    KubernetesHandler deployer = registry.get(kind).getHandler();

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(data);
    Moniker moniker = KubernetesCacheDataConverter.getMoniker(data);

    return new KubernetesV2Manifest().builder()
        .account(account)
        .location(location)
        .manifest(manifest)
        .moniker(moniker)
        .status(deployer.status(manifest))
        .build();
  }
}
