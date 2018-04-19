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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.RegistryUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public abstract class KubernetesV2CachingAgent extends KubernetesCachingAgent<KubernetesV2Credentials> {
  protected KubectlJobExecutor jobExecutor;

  @Getter
  protected String providerName = KubernetesCloudProvider.getID();

  private final KubernetesResourcePropertyRegistry propertyRegistry;

  protected KubernetesV2CachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubernetesResourcePropertyRegistry propertyRegistry,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
    this.propertyRegistry = propertyRegistry;
  }

  protected abstract KubernetesKind primaryKind();

  protected List<KubernetesKind> primaryKinds() {
    return Collections.singletonList(primaryKind());
  }

  // Cache types can choose to have relationships with Spinnaker 'clusters'
  protected boolean hasClusterRelationship() {
    return false;
  }

  protected Map<KubernetesKind, List<KubernetesManifest>> loadPrimaryResourceList() {
    return namespaces.stream()
        .map(n -> {
          try {
            return credentials.list(primaryKinds(), n);
          } catch (KubectlException e) {
            log.warn("Failed to read kind {} from namespace {}: {}", primaryKinds(), n, e.getMessage());
            return null;
          }
        })
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .collect(Collectors.groupingBy(KubernetesManifest::getKind));
  }

  protected KubernetesManifest loadPrimaryResource(KubernetesKind kind, String namespace, String name) {
    return credentials.get(kind, namespace, name);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + " is starting");
    reloadNamespaces();

    try {
      return buildCacheResult(loadPrimaryResourceList());
    } catch (KubectlJobExecutor.NoResourceTypeException e) {
      log.warn(getAgentType() + ": resource for this caching agent is not supported for this cluster");
      return new DefaultCacheResult(new HashMap<>());
    }
  }

  protected CacheResult buildCacheResult(KubernetesManifest resource) {
    return buildCacheResult(Collections.singletonMap(resource.getKind(), Collections.singletonList(resource)));
  }

  protected CacheResult buildCacheResult(Map<KubernetesKind, List<KubernetesManifest>> resources) {
    Map<KubernetesManifest, List<KubernetesManifest>> relationships = loadSecondaryResourceRelationships(resources);

    List<CacheData> resourceData = resources.values()
        .stream()
        .flatMap(Collection::stream)
        .peek(m -> RegistryUtils.removeSensitiveKeys(propertyRegistry, accountName, m))
        .map(rs -> KubernetesCacheDataConverter.convertAsResource(accountName, rs, relationships.get(rs), hasClusterRelationship()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    List<CacheData> invertedRelationships = resourceData.stream()
        .map(KubernetesCacheDataConverter::invertRelationships)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    resourceData.addAll(resources.values()
        .stream()
        .flatMap(Collection::stream)
        .map(rs -> KubernetesCacheDataConverter.convertAsArtifact(accountName, rs))
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));

    resourceData.addAll(invertedRelationships);

    Map<String, Collection<CacheData>> entries = KubernetesCacheDataConverter.stratifyCacheDataByGroup(KubernetesCacheDataConverter.dedupCacheData(resourceData));
    KubernetesCacheDataConverter.logStratifiedCacheData(getAgentType(), entries);

    return new DefaultCacheResult(entries);
  }

  protected Map<KubernetesManifest, List<KubernetesManifest>> loadSecondaryResourceRelationships(Map<KubernetesKind, List<KubernetesManifest>> primaryResourceList) {
    return new HashMap<>();
  }
}
