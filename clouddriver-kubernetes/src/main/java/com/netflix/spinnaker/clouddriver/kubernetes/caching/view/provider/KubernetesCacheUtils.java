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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesCacheUtils {
  private final Cache cache;
  private final KubernetesSpinnakerKindMap kindMap;
  private final KubernetesAccountResolver resourcePropertyResolver;

  @Autowired
  public KubernetesCacheUtils(
      Cache cache,
      KubernetesSpinnakerKindMap kindMap,
      KubernetesAccountResolver resourcePropertyResolver) {
    this.cache = cache;
    this.kindMap = kindMap;
    this.resourcePropertyResolver = resourcePropertyResolver;
  }

  public Collection<CacheData> getAllKeys(String type) {
    return cache.getAll(type);
  }

  public Collection<String> getAllKeysMatchingPattern(String type, String key) {
    return cache.filterIdentifiers(type, key);
  }

  public Collection<CacheData> getAllDataMatchingPattern(String type, String key) {
    return cache.getAll(type, getAllKeysMatchingPattern(type, key));
  }

  public Optional<CacheData> getSingleEntry(String type, String key) {
    return Optional.ofNullable(cache.get(type, key));
  }

  public Optional<CacheData> getSingleEntryWithRelationships(
      String type, String key, RelationshipCacheFilter cacheFilter) {
    return Optional.ofNullable(cache.get(type, key, cacheFilter));
  }

  /** Returns a collection of all relationships of a given SpinnakerKind for a CacheData. */
  ImmutableCollection<String> getRelationshipKeys(
      CacheData cacheData, SpinnakerKind spinnakerKind) {
    return relationshipTypes(spinnakerKind)
        .flatMap(t -> relationshipKeys(cacheData, t))
        .collect(toImmutableList());
  }

  public Collection<CacheData> getRelationships(@Nonnull CacheData cacheData, String to) {
    return cache.getAll(to, relationshipKeys(cacheData, to).collect(toImmutableSet()));
  }

  public Collection<CacheData> getRelationships(
      Collection<CacheData> cacheData, SpinnakerKind spinnakerKind) {
    return relationshipTypes(spinnakerKind)
        .map(kind -> loadRelationshipsFromCache(cacheData, kind))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  public Collection<CacheData> loadRelationshipsFromCache(
      CacheData source, String relationshipType) {
    return loadRelationshipsFromCache(ImmutableSet.of(source), relationshipType);
  }

  public Collection<CacheData> loadRelationshipsFromCache(
      Collection<CacheData> sources, String relationshipType) {
    return cache.getAll(
        relationshipType,
        sources.stream()
            .flatMap(cd -> relationshipKeys(cd, relationshipType))
            .collect(toImmutableSet()));
  }

  /*
   * Builds a map of all keys belonging to `sourceKind` that are related to any entries in `targetData`
   */
  public ImmutableMultimap<String, CacheData> mapByRelationship(
      Collection<CacheData> targetData, SpinnakerKind sourceKind) {
    ImmutableListMultimap.Builder<String, CacheData> builder = ImmutableListMultimap.builder();
    targetData.forEach(
        datum ->
            getRelationshipKeys(datum, sourceKind)
                .forEach(sourceKey -> builder.put(sourceKey, datum)));
    return builder.build();
  }

  /** Returns a stream of all relationships of a given type for a given CacheData. */
  @NonnullByDefault
  private Stream<String> relationshipKeys(CacheData cacheData, String type) {
    Collection<String> relationships = cacheData.getRelationships().get(type);
    // Avoiding creating an Optional here as this is deeply nested in performance-sensitive code.
    if (relationships == null) {
      return Stream.empty();
    }
    return relationships.stream();
  }

  /** Given a spinnaker kind, returns a stream of the relationship types representing that kind. */
  @NonnullByDefault
  private Stream<String> relationshipTypes(SpinnakerKind spinnakerKind) {
    return kindMap.translateSpinnakerKind(spinnakerKind).stream().map(KubernetesKind::toString);
  }

  @Nonnull
  KubernetesHandler getHandler(@Nonnull KubernetesV2CacheData cacheData) {
    Keys.InfrastructureCacheKey key =
        (Keys.InfrastructureCacheKey) Keys.parseKey(cacheData.primaryData().getId()).get();
    // TODO(ezimanyi): The kind is also stored directly on the cache data; get it from there instead
    // of reading it from the manifest.
    KubernetesKind kind =
        KubernetesCacheDataConverter.getManifest(cacheData.primaryData()).getKind();
    return resourcePropertyResolver
        .getResourcePropertyRegistry(key.getAccount())
        .get(kind)
        .getHandler();
  }
}
